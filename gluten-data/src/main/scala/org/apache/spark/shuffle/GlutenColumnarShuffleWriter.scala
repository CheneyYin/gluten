/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.shuffle

import java.io.IOException

import io.glutenproject.columnarbatch.GlutenColumnarBatches
import io.glutenproject.memory.alloc.{NativeMemoryAllocators, Spiller}
import io.glutenproject.GlutenConfig
import io.glutenproject.vectorized._

import org.apache.spark._
import org.apache.spark.internal.Logging
import org.apache.spark.memory.MemoryConsumer
import org.apache.spark.scheduler.MapStatus
import org.apache.spark.sql.vectorized.ColumnarBatch
import org.apache.spark.util.Utils

class GlutenColumnarShuffleWriter[K, V](shuffleBlockResolver: IndexShuffleBlockResolver,
                                        handle: BaseShuffleHandle[K, V, V],
                                        mapId: Long,
                                        writeMetrics: ShuffleWriteMetricsReporter)
  extends ShuffleWriter[K, V]
    with Logging {

  private val dep = handle.dependency.asInstanceOf[ColumnarShuffleDependency[K, V, V]]

  private val conf = SparkEnv.get.conf

  private val blockManager = SparkEnv.get.blockManager

  // Are we in the process of stopping? Because map tasks can call stop() with success = true
  // and then call stop() with success = false if they get an exception, we want to make sure
  // we don't try deleting files, etc twice.
  private var stopping = false

  private var mapStatus: MapStatus = _

  private val localDirs = blockManager.diskBlockManager.localDirs.mkString(",")

  private val offheapSize = conf.getSizeAsBytes("spark.memory.offHeap.size", 0)
  private val executorNum = conf.getInt("spark.executor.cores", 1)
  private val offheapPerTask = offheapSize / executorNum

  private val nativeBufferSize = GlutenConfig.getConf.shuffleSplitDefaultSize

  private val customizedCompressionCodec = {
    val codec = GlutenConfig.getConf.columnarShuffleUseCustomizedCompressionCodec
    val enableQat = conf.getBoolean(GlutenConfig.GLUTEN_ENABLE_QAT, false) &&
      GlutenConfig.GLUTEN_QAT_SUPPORTED_CODEC.contains(codec)
    if (enableQat) {
      GlutenConfig.GLUTEN_QAT_CODEC_PREFIX + codec
    } else {
      codec
    }
  }

  private val batchCompressThreshold =
    GlutenConfig.getConf.columnarShuffleBatchCompressThreshold

  private val preferSpill = GlutenConfig.getConf.columnarShufflePreferSpill

  private val writeSchema = GlutenConfig.getConf.columnarShuffleWriteSchema

  private val jniWrapper = new ShuffleSplitterJniWrapper

  private var nativeSplitter: Long = 0

  private var splitResult: SplitResult = _

  private var partitionLengths: Array[Long] = _

  private var rawPartitionLengths: Array[Long] = _

  @throws[IOException]
  def internalWrite(records: Iterator[Product2[K, V]]): Unit = {
    if (!records.hasNext) {
      partitionLengths = new Array[Long](dep.partitioner.numPartitions)
      shuffleBlockResolver.writeMetadataFileAndCommit(
        dep.shuffleId,
        mapId,
        partitionLengths,
        Array[Long](),
        null)
      mapStatus = MapStatus(blockManager.shuffleServerId, partitionLengths, mapId)
      return
    }

    val dataTmp = Utils.tempFileWith(shuffleBlockResolver.getDataFile(dep.shuffleId, mapId))
    if (nativeSplitter == 0) {
      nativeSplitter = jniWrapper.make(
        dep.nativePartitioning,
        offheapPerTask,
        nativeBufferSize,
        customizedCompressionCodec,
        batchCompressThreshold,
        dataTmp.getAbsolutePath,
        blockManager.subDirsPerLocalDir,
        localDirs,
        preferSpill,
        NativeMemoryAllocators.createSpillable(
          new Spiller() {
            override def spill(size: Long, trigger: MemoryConsumer): Long = {
              if (nativeSplitter == 0) {
                throw new IllegalStateException("Fatal: spill() called before a shuffle splitter " +
                  "evaluator is created. This behavior should be optimized by moving memory " +
                  "allocations from make() to split()")
              }
              logInfo(s"Gluten shuffle writer: Trying to spill $size bytes of data")
              // fixme pass true when being called by self
              val spilled = jniWrapper.nativeSpill(nativeSplitter, size, false)
              logInfo(s"Gluten shuffle writer: Spilled $spilled / $size bytes of data")
              spilled
            }
          }).getNativeInstanceId,
        writeSchema)
    }

    while (records.hasNext) {
      val cb = records.next()._2.asInstanceOf[ColumnarBatch]
      if (cb.numRows == 0 || cb.numCols == 0) {
        logInfo(s"Skip ColumnarBatch of ${cb.numRows} rows, ${cb.numCols} cols")
      } else {
        val startTime = System.nanoTime()
        val bytes = jniWrapper.split(nativeSplitter, cb.numRows,
          GlutenColumnarBatches.getNativeHandle(cb))
        dep.dataSize.add(bytes)
        dep.splitTime.add(System.nanoTime() - startTime)
        dep.numInputRows.add(cb.numRows)
        dep.inputBatches.add(1)
        // This metric is important, AQE use it to decide if EliminateLimit
        writeMetrics.incRecordsWritten(cb.numRows())
      }
    }

    val startTime = System.nanoTime()
    splitResult = jniWrapper.stop(nativeSplitter)

    dep.splitTime.add(System.nanoTime() - startTime - splitResult.getTotalSpillTime -
      splitResult.getTotalWriteTime -
      splitResult.getTotalCompressTime)
    dep.spillTime.add(splitResult.getTotalSpillTime)
    dep.compressTime.add(splitResult.getTotalCompressTime)
    dep.bytesSpilled.add(splitResult.getTotalBytesSpilled)
    writeMetrics.incBytesWritten(splitResult.getTotalBytesWritten)
    writeMetrics.incWriteTime(splitResult.getTotalWriteTime + splitResult.getTotalSpillTime)

    partitionLengths = splitResult.getPartitionLengths
    rawPartitionLengths = splitResult.getRawPartitionLengths
    try {
      shuffleBlockResolver.writeMetadataFileAndCommit(
        dep.shuffleId,
        mapId,
        partitionLengths,
        Array[Long](),
        dataTmp)
    } finally {
      if (dataTmp.exists() && !dataTmp.delete()) {
        logError(s"Error while deleting temp file ${dataTmp.getAbsolutePath}")
      }
    }

    // The partitionLength is much more than vanilla spark partitionLengths,
    // almost 3 times than vanilla spark partitionLengths
    // This value is sensitive in rules such as AQE rule OptimizeSkewedJoin DynamicJoinSelection
    // May affect the final plan
    mapStatus = MapStatus(blockManager.shuffleServerId, partitionLengths, mapId)
  }

  @throws[IOException]
  override def write(records: Iterator[Product2[K, V]]): Unit = {
    internalWrite(records)
  }

  def closeSplitter(): Unit = {
    jniWrapper.close(nativeSplitter)
  }

  override def stop(success: Boolean): Option[MapStatus] = {
    try {
      if (stopping) {
        None
      }
      stopping = true
      if (success) {
        Option(mapStatus)
      } else {
        None
      }
    } finally {
      if (nativeSplitter != 0) {
        closeSplitter()
        nativeSplitter = 0
      }
    }
  }

  def getPartitionLengths: Array[Long] = partitionLengths

}
