Help users to debug and test with gluten.

For intel internal developer, you could refer to internal wiki  [New Employee Guide](https://wiki.ith.intel.com/display/HPDA/New+Employee+Guide) to get more information such as proxy settings,
Gluten has cpp code and java/scala code, we can use some useful IDE to read and debug.

# Environment

Now gluten supports Ubuntu20.04, Ubuntu22.04, centos8

## Openjdk8

### Environment setting

For root user, the environment variables file is /etc/profile, it will make effect for all the users.

For other user, you can set in ~/.bashrc

### Guide for ubuntu
The default JDK version in ubuntu is java11, always we need to set to java8

```bash
apt install openjdk-8-jdk
update-alternatives --config java
java -version
```

`--config java` to config java executable path, `javac` and other command can also use this command to config
For some other uses, we suggest to set `JAVA_HOME`

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
JRE_HOME=$JAVA_HOME/jre
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
# pay attention to $PATH double quote
export PATH="$PATH:$JAVA_HOME/bin"
```

> Must set PATH with double quote in ubuntu

## Maven 3.6.3 or above

[Maven Dowload Page](https://maven.apache.org/docs/history.html)
And then se the environmen setting

## GCC 9.4 or above

# Compile gluten debug mode

If you just debug java code, you don't need to compile cpp debug mode, just do as [Velox.md](https://github.com/oap-project/gluten/blob/main/docs/Velox.md#2-build-gluten-with-velox-backend)

If you need to debug cpp code, please compile the backend code and gluten cpp code as debug mode

```bash
## compile velox
./build_velox.sh --build_type=Debug
## compile arrow with tests required library
./build_arrow_for_velox.sh --build_test=ON
## compile gluten cpp with benchmark and tests to debug
cmake -DBUILD_VELOX_BACKEND=ON -DBUILD_TESTS=ON -DBUILD_BENCHMARKS=ON -DCMAKE_BUILD_TYPE=Debug ..
```

If you need to debug the tests in <gluten>/gluten-ut
You need to compile java code with `-P spark-ut`

# Debug java/scala with Intellij

## Linux intellij local debug

Install the linux intellij version, and debug code locally

- Ask your linux maintainer to install the desktop, and then restart the server
- If you use Mobaxterm to connect linux server, you don't need to install x11 server, If not (e.g.  putty), please follow this guide
[X11 Forwarding: Setup Instructions for Linux and Mac](https://www.businessnewsdaily.com/11035-how-to-use-x11-forwarding.html)

- Download [intellij linux community version](https://www.jetbrains.com/idea/download/?fromIDE=#section=linux) to linux server
- Start Idea, bash <idea_dir>/idea.sh

Notes: Sometimes, your desktop may stop accidently, left idea running

```bash
root@xx2:~bash idea-IC-221.5787.30/bin/idea.sh
Already running
root@xx2:~ps ux | grep intellij
root@xx2:kill -9 <pid>
```

And then restart idea

## Windows/Mac intellij remote debug

If you have Ultimate intellij, you can try to debug remote

## Set up gluten project

- Make sure you have compiled gluten
- Load the gluten by File->Open, select <gluten_home/pom.xml>
- Activate your profiles such as <backends-velox>, and Reload Maven Project, you will find all your need modules have been activated
- Create breakpoint and debug as you wish, maybe you can try `CTRL+N` to find `TestOperator` to start your test

# Debug cpp code with Visual Studio Code

This guide is for remote debug. We will connect the remote linux server by `SSH`.
Download the [windows vscode software](https://code.visualstudio.com/Download)
The important leftside bar is

- Explorer (Project structure)
- Search
- Run and Debug
- Extensions (Install C/C++ Extension Pack, Remote Develoment, GitLens at least, C++ Test Mate is also suggested)
- Remote Explorer (Connect linux server by ssh command, click `+`, then input `ssh user@10.1.7.003`)
-
-
- Manage (Settings)

Input your password as above pop-up window, it will take few minutes to install linux vscode server in remote machine folder `~/.vscode-server`
If download failed, delete this folder and try again

## Usage

### Set up project

File->Open Folder   // select gluten folder
Select cpp/CmakeList.txt as command prompt
Select gcc version as command prompt

### Settings

Vscode support 2 ways to set user setting

- Manage->Command Palette(Open `settings.json`, search by `Preferences: Open Settings (JSON)`)
- Manage->Settings (Common setting)

### Build by vscode

Vscode will try to compile the debug version in <gluten_home>/build
And we need to compile velox debug mode before, if you have compiled velox release mode, you just need to do

```bash
# Build the velox debug version in <velox_home>/_build/debug
make debug EXTRA_CMAKE_FLAGS="-DVELOX_ENABLE_PARQUET=ON -DVELOX_ENABLE_HDFS=ON -DVELOX_BUILD_TESTING=OFF  -DVELOX_ENABLE_DUCKDB=ON -DVELOX_BUILD_TEST_UTILS=ON"
```

Then gluten will link velox debug library
Just click `build` in bottom bar, you will get intellisense search and link.

### Debug

The default compile command does not enable test and benchmark, so we cannot get any executable file
Open the file in `<gluten_home>/.vscode/settings.json` (create if not exists)

```json
{
    "cmake.configureArgs": [
        "-DBUILD_BENCHMARKS=ON",
        "-DBUILD_TESTS=ON"
    ],
    "C_Cpp.default.configurationProvider": "ms-vscode.cmake-tools"
}
```

Then we can get some executables, take `shuffle_test` as example

Click `Run and Debug` to create launch.json in `<gluten_home>/.vscode/launch.json`
Click `Add Configuration` in the top of launch.json, select gdb launch or attach to exists program
launch.json example

```json
{
  // Use IntelliSense to learn about possible attributes.
  // Hover to view descriptions of existing attributes.
  // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
  "version": "0.2.0",
  "configurations": [
    {
      "name": "shuffle test",
      "type": "cppdbg",
      "request": "launch",
      "program": "/mnt/DP_disk1/code/gluten/cpp/build/velox/shuffle_test",
      "args": ["--gtest_filter=*TestSingleSplitter*"],
      "stopAtEntry": false,
      "cwd": "${fileDirname}",
      "environment": [],
      "externalConsole": false,
      "MIMode": "gdb",
      "setupCommands": [
          {
              "description": "Enable pretty-printing for gdb",
              "text": "-enable-pretty-printing",
              "ignoreFailures": true
          },
          {
              "description": "Set Disassembly Flavor to Intel",
              "text": "-gdb-set disassembly-flavor intel",
              "ignoreFailures": true
          }
      ]
    },
    {
      "name": "benchmark test",
      "type": "cppdbg",
      "request": "launch",
      "program": "/mnt/DP_disk1/code/gluten/cpp/velox/benchmarks/./generic_benchmark",
      "args": ["/mnt/DP_disk1/code/gluten/cpp/velox/benchmarks/query.json", "--threads=1"],
      "stopAtEntry": false,
      "cwd": "${fileDirname}",
      "environment": [],
      "externalConsole": false,
      "MIMode": "gdb",
      "setupCommands": [
          {
              "description": "Enable pretty-printing for gdb",
              "text": "-enable-pretty-printing",
              "ignoreFailures": true
          },
          {
              "description": "Set Disassembly Flavor to Intel",
              "text": "-gdb-set disassembly-flavor intel",
              "ignoreFailures": true
          }
      ]
    }

  ]
}
```

> Change `name`, `program`, `args` to yours

Then you can create breakpoint and debug in `Run and Debug` section

### Velox debug

For some velox tests such as `ParquetReaderTest`, tests need to read the parquet file in `<velox_home>/velox/dwio/parquet/tests/examples`, you should let the screen on `ParquetReaderTest.cpp`, then click `Start Debuging`, otherwise you will raise No such file or directory exception

## Usefule notes

### Upgrade vscode

No need to upgrade vscode version, if upgraded, will download linux server again, switch update mode to off
Search `update` in Manage->Settings to turn off update mode

### Colour setting

```json
"workbench.colorTheme": "Quiet Light",
 "files.autoSave": "afterDelay",
 "workbench.colorCustomizations": {
     "editor.wordHighlightBackground": "#063ef7",
     // "editor.selectionBackground": "#d1d1c6",
     // "tab.activeBackground": "#b8b9988c",
     "editor.selectionHighlightBackground": "#c5293e"
 },
```

### Clang format

Now gluten use clang-format 11

```bash
apt-get install clang-format-11
```

Set config in `settings.json`

```json
"clang-format.executable": "clang-format-11",
"editor.formatOnSave": true,
```

If exists multiple clang-format version, formatOnSave may not take effect, specify the default formatter
Search `default formatter` in `Settings`, select Clang-Format

If your formatOnSave still make no effect, you can use shortcut `SHIFT+ALT+F` to format one file mannually

# Debug cpp code with coredump

```bash
mkdir -p /mnt/DP_disk1/core
sysctl -w kernel.core_pattern=/mnt/DP_disk1/core/core-%e-%p-%t
cat /proc/sys/kernel/core_pattern
# set the core file to unlimited size
echo "ulimit -c unlimited" >> ~/.bashrc
# then you will get core file at /mnt/DP_disk1/core when program crash
# gdb -c corefile
# gdb <gluten_home>/cpp/build/releases/libspark_columnar_jni.so 'core-Executor task l-2000883-1671542526'
```

'core-Executor task l-2000883-1671542526' is the gereated core file name

```bash
(gdb) bt
(gdb) f7
(gdb) set print pretty on
(gdb) p *this
```

- Get the backtrace
- Switch to 7th stack
- Print the variable in much readable way
- Print the variable fields
Sometimes you only get the cpp exception message, you can generate core dump file by

```cpp
char* p = nullptr;
*p = 'a';
```

# Debug cpp with gdb

You can use gdb to debug tests and benchmarks.
And also you can debug jni call.
Place following code to your debug path.

```cpp
pid_t pid = getpid();
printf("----------------------------------pid: %lun", pid);
sleep(10);
```

You can also get the pid by java command or grep java program when executing unit test.

```bash
jps
1375551 ScalaTestRunner
ps ux | grep TestOperator
```

Execute gdb command to debug

```bash
gdb attach <pid>
```

```bash
gdb attach 1375551
wait to attach....
(gdb) b <velox_home>/velox/substrait/SubstraitToVeloxPlan.cpp:577
(gdb) c
```

# Run TPC-H and TPC-DS

We supply `<gluten_home>/tools/gluten-it` to execute these queries
Refer to [unittest.yml](https://github.com/oap-project/gluten/blob/main/.github/workflows/unittests.yml)
