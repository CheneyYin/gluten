Currently, the mvn script can automatically fetch and build all dependency libraries incluing Velox and Arrow. Our nightly build still use Velox under oap-project. 

# 1 Prerequisite

Currently Gluten+Velox backend is only tested on <b>Ubuntu20.04</b>. Other kinds of OS support are still in progress </b>. The long term goal is to support several
common OS and conda env deployment.

Gluten builds with Spark3.2.x and Spark3.3.3 now but only fully tested in CI with 3.2.2 and 3.3.1. We will add/update supported/tested versions according to the upstream changes. 

Velox uses the script `setup-ubuntu.sh` to install all dependency libraries, but Arrow's dependency libraries are not installed. Velox also requires ninja for compilation.
So we need to install all of them manually. Also, we need to set up the `JAVA_HOME` env. Currently, <b>java 8</b> is required and the support for java 11/17 is not ready.

```shell script
## run as root
## install gcc and libraries to build arrow
apt-get update && apt-get install -y sudo locales wget tar tzdata git ccache cmake ninja-build build-essential llvm-11-dev clang-11 libiberty-dev libdwarf-dev libre2-dev libz-dev libssl-dev libboost-all-dev libcurl4-openssl-dev openjdk-8-jdk maven
```
<b>For x86_64</b>
```shell script
## make sure jdk8 is used
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```
<b>For aarch64</b>
```shell script
## make sure jdk8 is used
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
```
<b>Get gluten</b>
```shell script
## config maven, like proxy in ~/.m2/settings.xml

## fetch gluten code
git clone https://github.com/oap-project/gluten.git

```
# 2 Build Gluten with Velox Backend

It's recommended to use buildbundle-veloxbe.sh and build gluten in one script.
[Gluten Usage](./docs/GlutenUsage.md) listed the parameters and their default value of build command for your reference.

<b>For x86_64 build:</b>
```shell script
cd /path_to_gluten

## The script builds two jars for spark 3.2.2 and 3.3.1.
./dev/buildbundle-veloxbe.sh

## When you have successfully compiled once and changed some codes then compile again.
## you may use following command to skip the arrow, velox and protobuf build
# ./dev/buildbundle-veloxbe.sh --build_arrow_from_source=OFF --build_velox_from_source=OFF --build_protobuf=OFF

```

<b>For aarch64 build, set the CPU_TARGET to "aarch64":</b>
```shell script
export CPU_TARGET="aarch64"

cd /path_to_gluten

./dev/builddeps-veloxbe.sh
```

<b>Alternatively you may build gluten step by step as below.</b>

```shell script

## fetch arrow and compile
cd /path_to_gluten/ep/build-arrow/src/
./get_arrow.sh
./build_arrow_for_velox.sh

## fetch velox
cd /path_to_gluten/ep/build-velox/src/
./get_velox.sh
## compile velox
./build_velox.sh

## compile gluten cpp
cd /path_to_gluten/cpp
mkdir build
cd build
cmake -DBUILD_VELOX_BACKEND=ON ..
make -j

# You can also use CMAKE command to compile
# cd /path_to_gluten/cpp
# mkdir build && cd build && cmake .. && make -j

## compile gluten jvm and package jar
cd /path_to_gluten
# For spark3.2.x
mvn clean package -Pbackends-velox -Pspark-3.2 -DskipTests
# For spark3.3.x
mvn clean package -Pbackends-velox -Pspark-3.3 -DskipTests

```
notes：The compilation of `Velox` using the script of `build_velox.sh` may fail caused by `oom`, you can prevent this failure by using the user command of `export NUM_THREADS=4` before executing the above scripts.

Once building successfully, the Jar file will be generated in the directory: package/target/gluten-spark3.2_2.12-1.0.0-SNAPSHOT-jar-with-dependencies.jar for Spark 3.2.2, and package/target/gluten-spark3.3_2.12-1.0.0-SNAPSHOT-jar-with-dependencies.jar for Spark 3.3.1.

## 2.1 Specify velox home directory

You can also clone the Velox source from [OAP/velox](https://github.com/oap-project/velox) to some other folder then specify it as below.

```shell script
step 1: recompile velox, set velox_home in build_velox.sh
cd /path_to_gluten/ep/build_velox/src
./build_velox.sh  --velox_home=/your_specified_velox_path  --build_velox_backend=ON

step 2: recompile gluten cpp folder, set velox_home in build_velox.sh
cd /path_to_gluten/cpp
mkdir build
cd build
cmake -DBUILD_VELOX_BACKEND=ON -DVELOX_HOME=/your_specified_velox_path ..
make -j

step 3: package jar
cd /path_to_gluten
# For spark3.2.x
mvn clean package -Pbackends-velox -Pspark-3.2 -DskipTests
# For spark3.3.x
mvn clean package -Pbackends-velox -Pspark-3.3 -DskipTests

```

## 2.2 Arrow home directory

Arrow home can be set as the same of Velox. We will soon switch to upstream Arrow. Currently the shuffle still uses Arrow's IPC interface.
You can also clone the Arrow source from [OAP/Arrow](https://github.com/oap-project/arrow) to some other folder then specify it as below.

```shell script
step 1: set ARROW_SOURCE_DIR in build_arrow_for_velox.sh and compile
cd /path_to_gluten/ep/build-arrow/src/
./build_arrow_for_velox.sh

step 2: set ARROW_ROOT
cd /path_to_gluten/cpp
mkdir build
cd build
cmake -DBUILD_VELOX_BACKEND=ON -DARROW_ROOT=/your_arrow_lib ..
make -j

step 3: package jar
cd /path_to_gluten
# For spark3.2.x
mvn clean package -Pbackends-velox -Pspark-3.2 -DskipTests
# For spark3.3.x
mvn clean package -Pbackends-velox -Pspark-3.3 -DskipTests

```

## 2.3 HDFS support

Hadoop hdfs support is ready via the [libhdfs3](https://github.com/apache/hawq/tree/master/depends/libhdfs3) library. The libhdfs3 provides native API for Hadoop I/O without the drawbacks of JNI. It also provides advanced authentatication like Kerberos based. Please note this library has serveral depedencies which may require extra installations on Driver and Worker node.
On Ubuntu 20.04 the required depedencis are libiberty-dev, libxml2-dev, libkrb5-dev, libgsasl7-dev, libuuid1, uuid-dev. The packages can be installed via below command:
```
sudo apt install -y libiberty-dev libxml2-dev libkrb5-dev libgsasl7-dev libuuid1 uuid-dev
```
To build Gluten with HDFS support, below command is provided:
```
cd /path_to_gluten/ep/build-velox/src
./build_velox.sh --enable_hdfs=ON

cd /path_to_gluten/cpp
mkdir build
cd build
cmake -DBUILD_VELOX_BACKEND=ON -DENABLE_HDFS=ON ..
make -j

cd /path_to_gluten
mvn clean package -Pbackends-velox -Pspark-3.2 -Pfull-scala-compiler -DskipTests -Dcheckstyle.skip
```
Gluten HDFS support requires Hdfs URI, you can define this config in three ways:

1. spark-defaults.conf

```
spark.hadoop.fs.defaultFS hdfs://hdfshost:9000
```

2. envrionment variable `VELOX_HDFS`
```
// Spark local mode
export VELOX_HDFS="hdfs://hdfshost:9000"

// Spark Yarn cluster mode
--conf spark.executorEnv.VELOX_HDFS="hdfs://hdfshost:9000"
```

3. Hadoop's `core-site.xml` (recomended)
```
<property>
   <name>fs.defaultFS</name>
   <value>hdfs://hdfshost:9000</value>
</property>
```
If Gluten is used in a fully-prepared Hadoop cluster, we recommend to use Hadoop's config.

If your HDFS is in HA mode, you need to set the LIBHDFS3_CONF environment variable to specify the hdfs client configuration file.

```
// Spark local mode
export LIBHDFS3_CONF="/path/to/hdfs-client.xml"

// Spark Yarn cluster mode
--conf spark.executorEnv.LIBHDFS3_CONF="/path/to/hdfs-client.xml"
```
If Gluten is used in a fully-prepared Hadoop cluster, you can directly use the hdfs-site.xml of the cluster.

In the configuration file, you need to set the HDFS HA-related configuration.

```
<property>
   <name>dfs.nameservices</name>
   <value>EXAMPLENAMESERVICE</value>
</property>

<property>
    <name>dfs.ha.namenodes.EXAMPLENAMESERVICE</name>
    <value>ns1,ns2</value>
</property>

<property>
    <name>dfs.namenode.rpc-address.EXAMPLENAMESERVICE.ns1</name>
    <value>hdfs://ns1:8020</value>
</property>

<property>
    <name>dfs.namenode.rpc-address.EXAMPLENAMESERVICE.ns2</name>
    <value>hdfs://ns2:8020</value>
</property>
```

One typical deployment on Spark/HDFS cluster is to enable [short-circuit reading](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-hdfs/ShortCircuitLocalReads.html). Short-circuit reads provide a substantial performance boost to many applications.

By default libhdfs3 does not set the default hdfs domain socket path to support HDFS short-circuit read. If this feature is required in HDFS setup, users may need to setup the domain socket path correctly by patching the libhdfs3 source code or by setting the correct config environment. In Gluten the short-circuit domain socket path is set to "/var/lib/hadoop-hdfs/dn_socket" in [build_velox.sh](https://github.com/oap-project/gluten/blob/main/ep/build-velox/src/build_velox.sh) So we need to make sure the folder existed and user has write access as below script.

```
sudo mkdir -p /var/lib/hadoop-hdfs/
sudo chown <sparkuser>:<sparkuser> /var/lib/hadoop-hdfs/
```

You also need to add configuration to the "hdfs-site.xml" as below:

```
<property>
   <name>dfs.client.read.shortcircuit</name>
   <value>true</value>
</property>

<property>
   <name>dfs.domain.socket.path</name>
   <value>/var/lib/hadoop-hdfs/dn_socket</value>
</property>
```

## 2.4 Yarn Cluster mode

Hadoop Yarn mode is supported. Note libhdfs3 is used to read from HDFS, all its depedencies should be installed on each worker node. Users may requried to setup extra LD_LIBRARY_PATH if the depedencies are not on system's default library path. On Ubuntu 20.04 the dependencies can be installed with below command:
```
sudo apt install -y libiberty-dev libxml2-dev libkrb5-dev libgsasl7-dev libuuid1 uuid-dev
```
## 2.5 AWS S3 support

Velox supports S3 with the open source [AWS C++ SDK](https://github.com/aws/aws-sdk-cpp) and Gluten uses Velox S3 connector to connect with S3.
A new build option for S3(velox_enable_s3) is added. Below command is used to enable this feature
```
cd /path_to_gluten/ep/build-velox/src/
./build_velox.sh --enable_s3=ON

cd /path_to_gluten/cpp
mkdir build
cd build
cmake -DBUILD_VELOX_BACKEND=ON -DVELOX_ENABLE_S3=ON ..
make -j

cd /path_to_gluten
mvn clean package -Pbackends-velox -Pspark-3.2 -Pfull-scala-compiler -DskipTests -Dcheckstyle.skip
```
Currently to use S3 connector below configurations are required in spark-defaults.conf
```
spark.hadoop.fs.s3a.impl           org.apache.hadoop.fs.s3a.S3AFileSystem
spark.hadoop.fs.s3a.aws.credentials.provider org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider
spark.hadoop.fs.s3a.access.key     xxxx
spark.hadoop.fs.s3a.secret.key     xxxx
spark.hadoop.fs.s3a.endpoint https://s3.us-west-1.amazonaws.com
spark.hadoop.fs.s3a.connection.ssl.enabled true
spark.hadoop.fs.s3a.path.style.access false
```

You can also use instance credentials by setting the following config
```
spark.hadoop.fs.s3a.use.instance.credentials true
```
If you are using instance credentials you do not have to set the access key or secret key.

Note if testing with local S3-like service(Minio/Ceph), users may need to use different values for these configurations. E.g., on Minio setup, the "spark.hadoop.fs.s3a.path.style.access" need to set to "true".

# 3 Coverage
Spark3.3 has 387 functions in total. ~240 are commonly used. Velox's functions have two category, Presto and Spark. Presto has 124 functions implemented. Spark has 62 functions. Spark functions are verified to have the same result as Vanilla Spark. Some Presto functions have the same result as Vanilla Spark but some others have different. Gluten prefer to use Spark functions firstly. If it's not in Spark's list but implemented in Presto, we currently offload to Presto one until we noted some result mismatch, then we need to reimplement the function in Spark category. Gluten currently offloads 94 functions and 14 operators, more details refer to [The Operators and Functions Support Progress](https://github.com/oap-project/gluten/blob/main/docs/SupportProgress.md).

> Velox doesn't support [ANSI mode](https://spark.apache.org/docs/latest/sql-ref-ansi-compliance.html)), so as Gluten. Once ANSI mode is enabled in Spark config, Gluten will fallback to Vanilla Spark.


# 4 High-Bandwidth Memory (HBM) support

Gluten supports allocating memory on HBM. This feature is optional and is disabled by default. It is implemented on top of [Memkind library](http://memkind.github.io/memkind/). You can refer to memkind's [readme](https://github.com/memkind/memkind#memkind) for more details.

## 4.1 Build Gluten with HBM

Gluten will internally build and link to a specific version of Memkind library and [hwloc](https://github.com/open-mpi/hwloc). Other dependencies should be installed on Driver and Worker node first:

```shell script
sudo apt install -y autoconf automake g++ libnuma-dev libtool numactl unzip libdaxctl-dev
```

After the set-up, you can now build Gluten with HBM. Below command is used to enable this feature

```shell script
cd /path_to_gluten

## The script builds two jars for spark 3.2.2 and 3.3.1.
./dev/buildbundle-veloxbe.sh --enable_hbm=ON
```

## 4.2 Configure and enable HBM in Spark Application

At runtime, `MEMKIND_HBW_NODES` enviroment variable is detected for configuring HBM NUMA nodes. For the explaination to this variable, please refer to memkind's manual page. This can be set for all executors through spark conf, e.g. `--conf spark.executorEnv.MEMKIND_HBW_NODES=8-15`. Note that memory allocation fallback is also supported and cannot be turned off. If HBM is unavailable or fills up, the allocator will use default(DDR) memory.

# 5 Intel® QuickAssist Technology (QAT) support

Gluten supports using Intel® QuickAssist Technology (QAT) for data compression during Spark Shuffle. It benefits from QAT Hardware-based acceleration on compression/decompression, and uses Gzip as compression format for higher compression ratio to reduce the pressure on disks and network transmission.

This feature is based on QAT driver library and [QATzip](https://github.com/intel/QATzip) library. Please manually download QAT driver for your system, and follow its README to build and install on all Driver and Worker node: [Intel® QuickAssist Technology Driver for Linux* – HW Version 2.0](https://www.intel.com/content/www/us/en/download/765501/intel-quickassist-technology-driver-for-linux-hw-version-2-0.html?wapkw=quickassist).

Gluten will internally build and link to a specific version of QATzip library. Please **uninstall QATzip library** before building Gluten if it's already installed. Additional environment set-up are also required:

## 5.1 Build Gluten with QAT

1. Setup ICP_ROOT environment variable. This environment variable is required during building Gluten and running Spark applicaitons. It's recommended to put it in .bashrc on Driver and Worker node.

```shell script
export ICP_ROOT=/path_to_QAT_driver
```
2. **This step is required if your application is running as Non-root user**. The users must be added to the 'qat' group after QAT drvier is installed:

```shell script
sudo usermod -g qat username # need to relogin
```
Change the amount of max locked memory for the username that is included in the group name. This can be done by specifying the limit in /etc/security/limits.conf. To set 500MB add a line like this in /etc/security/limits.conf:

```shell script
cat /etc/security/limits.conf |grep qat
@qat - memlock 500000
```

3. Enable huge page as root user. **Note that this step is required to execute each time after system reboot.**

```shell script
 echo 1024 > /sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages
 rmmod usdm_drv
 insmod $ICP_ROOT/build/usdm_drv.ko max_huge_pages=1024 max_huge_pages_per_process=32
```
 
After the set-up, you can now build Gluten with QAT. Below command is used to enable this feature

```shell script
cd /path_to_gluten

## The script builds two jars for spark 3.2.2 and 3.3.1.
./dev/buildbundle-veloxbe.sh --enable_qat=ON
```

## 5.2 Enable QAT with Gzip Compression for shuffle compression

1. To enable QAT at run-time, first make sure you have the right QAT configuration file at /etc/4xxx_devX.conf. We provide a [example configuration file](qat/4x16.conf). This configuration sets up to 4 processes that can bind to 1 QAT, and each process can use up to 16 QAT DC instances.

```shell script
## run as root
## Overwrite QAT configuration file.
cd /etc
for i in {0..7}; do echo "4xxx_dev$i.conf"; done | xargs -i cp -f /path_to_gluten/docs/qat/4x16.conf {}
## Restart QAT after updating configuration files.
adf_ctl restart
```

2. Check QAT status and make sure the status is up

```shell script
adf_ctl status
```

The output should be like:

```
Checking status of all devices.
There is 8 QAT acceleration device(s) in the system:
 qat_dev0 - type: 4xxx,  inst_id: 0,  node_id: 0,  bsf: 0000:6b:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev1 - type: 4xxx,  inst_id: 1,  node_id: 1,  bsf: 0000:70:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev2 - type: 4xxx,  inst_id: 2,  node_id: 2,  bsf: 0000:75:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev3 - type: 4xxx,  inst_id: 3,  node_id: 3,  bsf: 0000:7a:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev4 - type: 4xxx,  inst_id: 4,  node_id: 4,  bsf: 0000:e8:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev5 - type: 4xxx,  inst_id: 5,  node_id: 5,  bsf: 0000:ed:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev6 - type: 4xxx,  inst_id: 6,  node_id: 6,  bsf: 0000:f2:00.0,  #accel: 1 #engines: 9 state: up
 qat_dev7 - type: 4xxx,  inst_id: 7,  node_id: 7,  bsf: 0000:f7:00.0,  #accel: 1 #engines: 9 state: up
```

2. Extra Gluten configurations are required when starting Spark application

```
--conf spark.gluten.sql.columnar.qat=true
--conf spark.gluten.sql.columnar.shuffle.customizedCompression.codec=gzip
```

3. You can use below command to check whether QAT is working normally at run-time. The value of fw_counters should continue to increase during shuffle. 

```
while :; do cat /sys/kernel/debug/qat_4xxx_0000:6b:00.0/fw_counters; sleep 1; done
```

## 5.3 QAT driver references

**Documentation**

[README Text Files (README_QAT20.L.1.0.0-00021.txt)](https://downloadmirror.intel.com/765523/README_QAT20.L.1.0.0-00021.txt)

**Release Notes**

Check out the [Intel® QuickAssist Technology Software for Linux*](https://www.intel.com/content/www/us/en/content-details/632507/intel-quickassist-technology-intel-qat-software-for-linux-release-notes-hardware-version-2-0.html) - Release Notes for the latest changes in this release.

**Getting Started Guide**

Check out the [Intel® QuickAssist Technology Software for Linux*](https://www.intel.com/content/www/us/en/content-details/632506/intel-quickassist-technology-intel-qat-software-for-linux-getting-started-guide-hardware-version-2-0.html) - Getting Started Guide for detailed installation instructions.

**Programmer's Guide**

Check out the [Intel® QuickAssist Technology Software for Linux*](https://www.intel.com/content/www/us/en/content-details/743912/intel-quickassist-technology-intel-qat-software-for-linux-programmers-guide-hardware-version-2-0.html) - Programmer's Guide for software usage guidelines.

For more Intel® QuickAssist Technology resources go to [Intel® QuickAssist Technology (Intel® QAT)](https://developer.intel.com/quickassist)

# 6 Test TPC-H on Gluten with Velox backend

In Gluten, all 22 queries can be fully offloaded into Velox for computing.  

## 6.1 Data preparation

Considering current Velox does not fully support Decimal and Date data type, the [datagen script](../backends-velox/workload/tpch/gen_data/parquet_dataset/tpch_datagen_parquet.scala) transforms "Decimal-to-Double" and "Date-to-String". As a result, we need to modify the TPCH queries a bit. You can find the [modified TPC-H queries](../backends-velox/workload/tpch/tpch.queries.updated/).

## 6.2 Submit the Spark SQL job

Submit test script from spark-shell. You can find the scala code to [Run TPC-H](../backends-velox/workload/tpch/run_tpch/tpch_parquet.scala) as an example. Please remember to modify the location of TPC-H files as well as TPC-H queries in backends-velox/workload/tpch/run_tpch/tpch_parquet.scala before you run the testing. 

```
var parquet_file_path = "/PATH/TO/TPCH_PARQUET_PATH"
var gluten_root = "/PATH/TO/GLUTEN"
```

Below script shows an example about how to run the testing, you should modify the parameters such as executor cores, memory, offHeap size based on your environment. 

```shell script
export GLUTEN_JAR = /PATH/TO/GLUTEN/backends-velox/target/gluten-spark3.2_2.12-1.0.0-snapshot-jar-with-dependencies.jar 
cat tpch_parquet.scala | spark-shell --name tpch_powertest_velox \
  --master yarn --deploy-mode client \
  --conf spark.plugins=io.glutenproject.GlutenPlugin \
  --conf spark.gluten.sql.columnar.backend.lib=velox \
  --conf spark.driver.extraClassPath=${GLUTEN_JAR} \
  --conf spark.executor.extraClassPath=${GLUTEN_JAR} \
  --conf spark.memory.offHeap.enabled=true \
  --conf spark.memory.offHeap.size=20g \
  --conf spark.gluten.sql.columnar.forceshuffledhashjoin=true \
  --conf spark.shuffle.manager=org.apache.spark.shuffle.sort.ColumnarShuffleManager \
  --num-executors 6 \
  --executor-cores 6 \
  --driver-memory 20g \
  --executor-memory 25g \
  --conf spark.executor.memoryOverhead=5g \
  --conf spark.driver.maxResultSize=32g
```

Refer to [Gluten parameters ](./Configuration.md) for more details of each parameter used by Gluten.

## 6.3 Result
*wholestagetransformer* indicates that the offload works.

![TPC-H Q6](./image/TPC-H_Q6_DAG.png)

## 6.4 Performance

Below table shows the TPC-H Q1 and Q6 Performance in a multiple-thread test (--num-executors 6 --executor-cores 6) for Velox and vanilla Spark.
Both Parquet and ORC datasets are sf1024.

| Query Performance (s) | Velox (ORC) | Vanilla Spark (Parquet) | Vanilla Spark (ORC) |
|---------------- | ----------- | ------------- | ------------- |
| TPC-H Q6 | 13.6 | 21.6  | 34.9 |
| TPC-H Q1 | 26.1 | 76.7 | 84.9 |

# 7 External reference setup

TO ease your first-hand experience of using Gluten, we have set up an external reference cluster. If you are interested, please contact Weiting.Chen@intel.com.
