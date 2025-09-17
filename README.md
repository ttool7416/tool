# Tool

> A tool to detect upgrade bugs of distributed systems

## Feature
* Coverage-guided fuzz testing
  * upfuzz collects branch coverage of the cluster to guide the testing
  process.
  * upfuzz implements a type system for mutation. Users only need to
      implement their command via the given types, and upfuzz can
      generate/mutate valid command sequence.
* Fault Injection
* Inconsistency Detector
    * Compare read results between different versions.
* Configuration Testing
* Format Coverage
* Nyx-snapshot for system start up

## Prerequisite
```bash
# jdk
sudo apt-get install openjdk-11-jdk openjdk-8-jdk

# docker
sudo apt-get update
sudo apt-get install \
    ca-certificates \
    curl \
    gnupg \
    lsb-release -y
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin -y
```

## Data Format Testing
> Check out dinv-monitor about how to create an instrumented tarball

1. Use a format instrumented tarball.
2. Make sure the `configInfo/system-x.x.x` contain `serializedFields_alg1.json` and `topObjects.json` file. (They should be the same as the one under the instrumented system binary).
3. Enable `useFormatCoverage` in configuration file.

The other steps are the same as the normal testing.

## Note: avoid conflict with other upfuzz users
If multiple upfuzz users are using the same server, the default port may conflict.

Solution: Set the `serverPort` and `clientPort` in the configuration file to avoid conflict with other users.
```json
"serverPort" : "?",
"clientPort" : "?",
```

## Minimal Set up for Cassandra (Try upfuzz quickly!)
Requirement: java11, docker (Docker version 26.0.0, build a5ee5b1)
> - Not test configurations.
> - Single Cassandra node upgrade: 3.11.17 => 4.1.4
> - If using Nyx Mode, please clone the upfuzz repo at first and then follow the guide at `nyx_mode/README.md` before continuing.

### Test single version

```bash
ssh-keyscan github.com >> ~/.ssh/known_hosts

cd upfuzz

export UPFUZZ_DIR=$PWD
export ORI_VERSION=3.11.17
mkdir -p ${UPFUZZ_DIR}/prebuild/cassandra
cd ${UPFUZZ_DIR}/prebuild/cassandra
wget https://archive.apache.org/dist/cassandra/"$ORI_VERSION"/apache-cassandra-"$ORI_VERSION"-bin.tar.gz ; tar -xzvf apache-cassandra-"$ORI_VERSION"-bin.tar.gz
cd ${UPFUZZ_DIR}
cp src/main/resources/cqlsh_daemon2.py prebuild/cassandra/apache-cassandra-"$ORI_VERSION"/bin/cqlsh_daemon.py
cd src/main/resources/cassandra/single-version-testing
sudo chmod 666 /var/run/docker.sock
docker build . -t upfuzz_cassandra:apache-cassandra-"$ORI_VERSION"
cd ${UPFUZZ_DIR}
./gradlew copyDependencies
./gradlew :spotlessApply build

sed -i 's/"testSingleVersion": false,/"testSingleVersion": true,/g' config.json

# open terminal1: start server
bin/start_server.sh config.json
# open terminal2: start one client
bin/start_clients.sh 1 config.json

# stop testing
bin/cass_cl.sh
```

### Test upgrade process
```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=3.11.17
export UP_VERSION=4.1.4

mkdir -p "$UPFUZZ_DIR"/prebuild/cassandra
cd prebuild/cassandra
wget https://archive.apache.org/dist/cassandra/"$ORI_VERSION"/apache-cassandra-"$ORI_VERSION"-bin.tar.gz ; tar -xzvf apache-cassandra-"$ORI_VERSION"-bin.tar.gz
wget https://archive.apache.org/dist/cassandra/"$UP_VERSION"/apache-cassandra-"$UP_VERSION"-bin.tar.gz ; tar -xzvf apache-cassandra-"$UP_VERSION"-bin.tar.gz
sed -i 's/num_tokens: 16/num_tokens: 256/' apache-cassandra-"$UP_VERSION"/conf/cassandra.yaml

cd ${UPFUZZ_DIR}
cp src/main/resources/cqlsh_daemon2.py prebuild/cassandra/apache-cassandra-"$ORI_VERSION"/bin/cqlsh_daemon.py
cp src/main/resources/cqlsh_daemon4.py  prebuild/cassandra/apache-cassandra-"$UP_VERSION"/bin/cqlsh_daemon.py

cd src/main/resources/cassandra/normal/compile-src/
sed -i "s/ORI_VERSION=apache-cassandra-.*$/ORI_VERSION=apache-cassandra-$ORI_VERSION/" cassandra-clusternode.sh
sed -i "s/UP_VERSION=apache-cassandra-.*$/UP_VERSION=apache-cassandra-$UP_VERSION/" cassandra-clusternode.sh
docker build . -t upfuzz_cassandra:apache-cassandra-"$ORI_VERSION"_apache-cassandra-"$UP_VERSION"

cd ${UPFUZZ_DIR}
./gradlew copyDependencies
./gradlew :spotlessApply build

# open terminal1: start server
bin/start_server.sh config.json
# open terminal2: start one client
bin/start_clients.sh 1 config.json

# stop testing
bin/cass_cl.sh
```

## Minimal Set up for HDFS (Try upfuzz quickly!)
Requirement: jdk8, jdk11, docker (Docker version 26.0.0)
> - Not test configurations.
> - 4 Nodes upgrade (NN, SNN, 2DN) ORI_VERSION => UP_VERSION

```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=2.10.2
export UP_VERSION=3.3.6

mkdir -p $UPFUZZ_DIR/prebuild/hdfs
cd $UPFUZZ_DIR/prebuild/hdfs
wget https://archive.apache.org/dist/hadoop/common/hadoop-"$ORI_VERSION"/hadoop-"$ORI_VERSION".tar.gz ; tar -xzvf hadoop-$ORI_VERSION.tar.gz
wget https://archive.apache.org/dist/hadoop/common/hadoop-"$UP_VERSION"/hadoop-"$UP_VERSION".tar.gz ; tar -xzvf hadoop-"$UP_VERSION".tar.gz

# old version hdfs daemon
cp $UPFUZZ_DIR/src/main/resources/FsShellDaemon2.java $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$ORI_VERSION"/FsShellDaemon.java
cd $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$ORI_VERSION"/
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/elif \[ \"\$COMMAND\" = \"dfs\" \] ; then/elif [ \"\$COMMAND\" = \"dfsdaemon\" ] ; then\n  CLASS=org.apache.hadoop.fs.FsShellDaemon\n  HADOOP_OPTS=\"\$HADOOP_OPTS \$HADOOP_CLIENT_OPTS\"\n&/" bin/hdfs

# new version hdfs daemon
cp $UPFUZZ_DIR/src/main/resources/FsShellDaemon_trunk.java $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$UP_VERSION"/FsShellDaemon.java
cd $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$UP_VERSION"/
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/  case \${subcmd} in/&\n    dfsdaemon)\n      HADOOP_CLASSNAME=\"org.apache.hadoop.fs.FsShellDaemon\"\n    ;;/" bin/hdfs

cd $UPFUZZ_DIR/src/main/resources/hdfs/compile-src/
sed -i "s/ORG_VERSION=hadoop-.*$/ORG_VERSION=hadoop-$ORI_VERSION/" hdfs-clusternode.sh
sed -i "s/UPG_VERSION=hadoop-.*$/UPG_VERSION=hadoop-$UP_VERSION/" hdfs-clusternode.sh
docker build . -t upfuzz_hdfs:hadoop-"$ORI_VERSION"_hadoop-"$UP_VERSION"

cd $UPFUZZ_DIR
./gradlew copyDependencies
./gradlew :spotlessApply build

# open terminal1: start server
bin/start_server.sh hdfs_config.json
# open terminal2: start one client
bin/start_clients.sh 1 hdfs_config.json

# stop testing
bin/hdfs_cl.sh
```

**Critical configuration** setting which affects the hdfs upgrade process. Normally `prepareImageFirst` should be false. But to test log replay, we need to set it to true.
```
# hdfs_config.json
prepareImageFirst
```

## Minimal Set up for HBase (Try upfuzz quickly!)

```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=2.4.18
export UP_VERSION=2.5.9

mkdir -p $UPFUZZ_DIR/prebuild/hadoop
cd $UPFUZZ_DIR/prebuild/hadoop
wget https://archive.apache.org/dist/hadoop/common/hadoop-2.10.2/hadoop-2.10.2.tar.gz ; tar -xzvf hadoop-2.10.2.tar.gz
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/core-site.xml $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/hdfs-site.xml $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/hadoop-env.sh $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f

mkdir -p $UPFUZZ_DIR/prebuild/hbase
cd $UPFUZZ_DIR/prebuild/hbase
wget https://archive.apache.org/dist/hbase/"$ORI_VERSION"/hbase-"$ORI_VERSION"-bin.tar.gz -O hbase-"$ORI_VERSION".tar.gz ; tar -xzvf hbase-"$ORI_VERSION".tar.gz
wget https://archive.apache.org/dist/hbase/"$UP_VERSION"/hbase-"$UP_VERSION"-bin.tar.gz -O hbase-"$UP_VERSION".tar.gz ; tar -xzvf hbase-"$UP_VERSION".tar.gz
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase-env.sh $UPFUZZ_DIR/prebuild/hbase/hbase-$ORI_VERSION/conf/ -f
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase-env.sh $UPFUZZ_DIR/prebuild/hbase/hbase-$UP_VERSION/conf/ -f

# If testing 3.0.0
# cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase-env-jdk17.sh $UPFUZZ_DIR/prebuild/hbase/hbase-$UP_VERSION/conf/hbase-env.sh -f


# for hbase version >= 2.4.0, use hbase_daemon3.py
# for hbase version < 2.4.0, use hbase_daemon2.py
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase_daemon3.py $UPFUZZ_DIR/prebuild/hbase/hbase-$ORI_VERSION/bin/hbase_daemon.py
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase_daemon3.py $UPFUZZ_DIR/prebuild/hbase/hbase-$UP_VERSION/bin/hbase_daemon.py


cd $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/
docker build . -t upfuzz_hdfs:hadoop-2.10.2

cd $UPFUZZ_DIR/src/main/resources/hbase/compile-src/
docker build . -t upfuzz_hbase:hbase-"$ORI_VERSION"_hbase-"$UP_VERSION"

cd $UPFUZZ_DIR
./gradlew copyDependencies
./gradlew :spotlessApply build

# open terminal1: start server (this runs in foreground)
bin/start_server.sh hbase_config.json
# open terminal2: start one client (this runs in background)
bin/start_clients.sh 1 hbase_config.json

# stop testing
bin/hbase_cl.sh
```

## Minimal Set up for Ozone (Try upfuzz quickly!)

```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=1.2.1
export UP_VERSION=1.3.0

mkdir -p $UPFUZZ_DIR/prebuild/ozone
cd $UPFUZZ_DIR/prebuild/ozone
wget https://archive.apache.org/dist/ozone/$ORI_VERSION/ozone-$ORI_VERSION.tar.gz ; tar -xzvf ozone-$ORI_VERSION.tar.gz
wget https://archive.apache.org/dist/ozone/$UP_VERSION/ozone-$UP_VERSION.tar.gz ; tar -xzvf ozone-$UP_VERSION.tar.gz

# Daemon
cp $UPFUZZ_DIR/src/main/resources/ozone/compile-src/OzoneFsShellDaemon.java $UPFUZZ_DIR/prebuild/ozone/ozone-"$ORI_VERSION"/OzoneFsShellDaemon.java
cp $UPFUZZ_DIR/src/main/resources/ozone/compile-src/OzoneFsShellDaemon_2.java $UPFUZZ_DIR/prebuild/ozone/ozone-"$UP_VERSION"/OzoneFsShellDaemon.java

cd $UPFUZZ_DIR/prebuild/ozone/ozone-"$ORI_VERSION"
sed -i '/^\s*fs)/i\    fsShellDaemon)\n      OZONE_CLASSNAME=org.apache.hadoop.ozone.OzoneFsShellDaemon\n      OZONE_RUN_ARTIFACT_NAME="ozone-tools"\n    ;;' bin/ozone
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/ozone/lib/*" OzoneFsShellDaemon.java

cd $UPFUZZ_DIR/prebuild/ozone/ozone-"$UP_VERSION"
sed -i '/^\s*fs)/i\    fsShellDaemon)\n      OZONE_CLASSNAME=org.apache.hadoop.ozone.OzoneFsShellDaemon\n      OZONE_CLIENT_OPTS="-Dhadoop.log.file=ozone-shell.log -Dlog4j.configuration=file:${ozone_shell_log4j} ${OZONE_CLIENT_OPTS}"\n      OZONE_RUN_ARTIFACT_NAME="ozone-tools"\n    ;;' bin/ozone
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/ozone/lib/*" OzoneFsShellDaemon.java

cd $UPFUZZ_DIR/src/main/resources/ozone/compile-src/
sed -i "s/ORI_VERSION=ozone-.*$/ORI_VERSION=ozone-$ORI_VERSION/" ozone-clusternode.sh
sed -i "s/UP_VERSION=ozone-.*$/UP_VERSION=ozone-$UP_VERSION/" ozone-clusternode.sh
docker build . -t upfuzz_ozone:ozone-"$ORI_VERSION"_ozone-"$UP_VERSION"

cd $UPFUZZ_DIR
./gradlew copyDependencies
./gradlew :spotlessApply build

# Create session + Test run
tmux new-session -d -s 0 \; split-window -v \;
tmux send-keys -t 0:0.0 C-m 'bin/start_server.sh ozone_config.json' C-m \;
tmux send-keys -t 0:0.1 C-m 'sleep 2; bin/start_clients.sh 1 ozone_config.json' C-m

# stop testing
bin/ozone_cl.sh $ORI_VERSION $UP_VERSION
```

## Version Delta Testing

Latest implementation only applies to one-group set up.
* Set `useVersionDelta` to `true`.
* Set `versionDeltaApproach` to `1`. (Represents 1 group setup)


### Cassandra
```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=3.11.17
export UP_VERSION=4.1.4

mkdir -p "$UPFUZZ_DIR"/prebuild/cassandra
cd prebuild/cassandra
wget https://archive.apache.org/dist/cassandra/"$ORI_VERSION"/apache-cassandra-"$ORI_VERSION"-bin.tar.gz ; tar -xzvf apache-cassandra-"$ORI_VERSION"-bin.tar.gz
wget https://archive.apache.org/dist/cassandra/"$UP_VERSION"/apache-cassandra-"$UP_VERSION"-bin.tar.gz ; tar -xzvf apache-cassandra-"$UP_VERSION"-bin.tar.gz
sed -i 's/num_tokens: 16/num_tokens: 256/' apache-cassandra-"$UP_VERSION"/conf/cassandra.yaml

cd ${UPFUZZ_DIR}
cp src/main/resources/cqlsh_daemon2.py prebuild/cassandra/apache-cassandra-"$ORI_VERSION"/bin/cqlsh_daemon.py
cp src/main/resources/cqlsh_daemon4.py  prebuild/cassandra/apache-cassandra-"$UP_VERSION"/bin/cqlsh_daemon.py

cd src/main/resources/cassandra/normal/compile-src/
sed -i "s/ORI_VERSION=apache-cassandra-.*$/ORI_VERSION=apache-cassandra-$ORI_VERSION/" cassandra-clusternode.sh
sed -i "s/UP_VERSION=apache-cassandra-.*$/UP_VERSION=apache-cassandra-$UP_VERSION/" cassandra-clusternode.sh
docker build . -t upfuzz_cassandra:apache-cassandra-"$ORI_VERSION"_apache-cassandra-"$UP_VERSION"
# modify the cassandra-clusternode.sh file: reverse ORI_VERSION and UP_VERSION
sed -i "s/ORI_VERSION=apache-cassandra-.*$/ORI_VERSION=apache-cassandra-$UP_VERSION/" cassandra-clusternode.sh
sed -i "s/UP_VERSION=apache-cassandra-.*$/UP_VERSION=apache-cassandra-$ORI_VERSION/" cassandra-clusternode.sh
docker build . -t upfuzz_cassandra:apache-cassandra-"$UP_VERSION"_apache-cassandra-"$ORI_VERSION"

cd ${UPFUZZ_DIR}
./gradlew copyDependencies
./gradlew :spotlessApply build

# open terminal1: start server
bin/start_server.sh config.json
bin/start_clients.sh 1 config.json

# stop testing
bin/cass_cl.sh 3.11.17 4.1.4
bin/cass_cl.sh 4.1.4 3.11.17
```

### HDFS

```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=2.10.2
export UP_VERSION=3.3.6

mkdir -p $UPFUZZ_DIR/prebuild/hdfs
cd $UPFUZZ_DIR/prebuild/hdfs
wget https://archive.apache.org/dist/hadoop/common/hadoop-"$ORI_VERSION"/hadoop-"$ORI_VERSION".tar.gz ; tar -xzvf hadoop-$ORI_VERSION.tar.gz
wget https://archive.apache.org/dist/hadoop/common/hadoop-"$UP_VERSION"/hadoop-"$UP_VERSION".tar.gz ; tar -xzvf hadoop-"$UP_VERSION".tar.gz

# old version hdfs daemon
cp $UPFUZZ_DIR/src/main/resources/FsShellDaemon2.java $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$ORI_VERSION"/FsShellDaemon.java
cd $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$ORI_VERSION"/
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/elif \[ \"\$COMMAND\" = \"dfs\" \] ; then/elif [ \"\$COMMAND\" = \"dfsdaemon\" ] ; then\n  CLASS=org.apache.hadoop.fs.FsShellDaemon\n  HADOOP_OPTS=\"\$HADOOP_OPTS \$HADOOP_CLIENT_OPTS\"\n&/" bin/hdfs

# new version hdfs daemon
cp $UPFUZZ_DIR/src/main/resources/FsShellDaemon_trunk.java $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$UP_VERSION"/FsShellDaemon.java
cd $UPFUZZ_DIR/prebuild/hdfs/hadoop-"$UP_VERSION"/
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/  case \${subcmd} in/&\n    dfsdaemon)\n      HADOOP_CLASSNAME=\"org.apache.hadoop.fs.FsShellDaemon\"\n    ;;/" bin/hdfs

cd $UPFUZZ_DIR/src/main/resources/hdfs/compile-src/
sed -i "s/ORG_VERSION=hadoop-.*$/ORG_VERSION=hadoop-$ORI_VERSION/" hdfs-clusternode.sh
sed -i "s/UPG_VERSION=hadoop-.*$/UPG_VERSION=hadoop-$UP_VERSION/" hdfs-clusternode.sh
docker build . -t upfuzz_hdfs:hadoop-"$ORI_VERSION"_hadoop-"$UP_VERSION"
# replace up and down version
sed -i "s/ORG_VERSION=hadoop-.*$/ORG_VERSION=hadoop-$UP_VERSION/" hdfs-clusternode.sh
sed -i "s/UPG_VERSION=hadoop-.*$/UPG_VERSION=hadoop-$ORI_VERSION/" hdfs-clusternode.sh
docker build . -t upfuzz_hdfs:hadoop-"$UP_VERSION"_hadoop-"$ORI_VERSION"

cd $UPFUZZ_DIR
./gradlew copyDependencies
./gradlew :spotlessApply build

# open terminal1: start server
bin/start_server.sh hdfs_config.json
# open terminal2: start one client
bin/start_clients.sh 1 hdfs_config.json

# stop testing
bin/hdfs_cl.sh
```

### HBase
```bash

cd upfuzz
export UPFUZZ_DIR=$PWD
export ORI_VERSION=2.4.19
export UP_VERSION=2.5.9

mkdir -p $UPFUZZ_DIR/prebuild/hadoop
cd $UPFUZZ_DIR/prebuild/hadoop
wget https://archive.apache.org/dist/hadoop/common/hadoop-2.10.2/hadoop-2.10.2.tar.gz ; tar -xzvf hadoop-2.10.2.tar.gz
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/core-site.xml $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/hdfs-site.xml $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f
cp $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/hadoop-env.sh $UPFUZZ_DIR/prebuild/hadoop/hadoop-2.10.2/etc/hadoop/ -f

mkdir -p $UPFUZZ_DIR/prebuild/hbase
cd $UPFUZZ_DIR/prebuild/hbase
wget https://archive.apache.org/dist/hbase/"$ORI_VERSION"/hbase-"$ORI_VERSION"-bin.tar.gz -O hbase-"$ORI_VERSION".tar.gz ; tar -xzvf hbase-"$ORI_VERSION".tar.gz
wget https://archive.apache.org/dist/hbase/"$UP_VERSION"/hbase-"$UP_VERSION"-bin.tar.gz -O hbase-"$UP_VERSION".tar.gz ; tar -xzvf hbase-"$UP_VERSION".tar.gz
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase-env.sh $UPFUZZ_DIR/prebuild/hbase/hbase-$ORI_VERSION/conf/ -f
cp $UPFUZZ_DIR/src/main/resources/hbase/compile-src/hbase-env.sh $UPFUZZ_DIR/prebuild/hbase/hbase-$UP_VERSION/conf/ -f

cd $UPFUZZ_DIR/src/main/resources/hdfs/hbase-pure/
docker build . -t upfuzz_hdfs:hadoop-2.10.2

cd $UPFUZZ_DIR/src/main/resources/hbase/compile-src/
docker build . -t upfuzz_hbase:hbase-"$ORI_VERSION"_hbase-"$UP_VERSION"
docker build . -t upfuzz_hbase:hbase-"$UP_VERSION"_hbase-"$ORI_VERSION"

cd $UPFUZZ_DIR
./gradlew copyDependencies
./gradlew :spotlessApply build

# Enable version delta testing mechanism in hbase_config.json
# open terminal1: start server
bin/start_server.sh hbase_config.json
# open terminal2: start one client
bin/start_clients.sh 1 hbase_config.json

# stop testing
bin/hbase_cl.sh
```

## Usage

Important configurations
- **testingMode**
  - 0: Execute stacked test packets.
  - 4: Execute stacked test packets and test plan (with fault injection) recursively.

> Config test is disabled by default.
>
> If you want to test configuration, checkout **docs/TEST_CONFIG.md**.

### Testing through UpFuzz image with all dependencies enabled
* You can avoid setting up all the prerequisites by connecting to our image hosted in chameleon cloud

```bash
ssh upfuzz@192.5.87.94
# password: 123
```

* After logging in, you can proceed with the next steps
* Instead of cloning git repository and switching branch again, you can pull
```bash
cd $UPFUZZ_DIR
git pull
```
* You can avoid setting up UPFUZZ_DIR again if you test through this image
* Start from setting the ORI_VERSION and the UP_VERSION

### Failure
The failures are stored under the `failure` folder. The number in `fullSequence_TIME.report` denotes the elapsed time to detect the failure. For example, the following failure is detected at 121 seconds.
```bash
node1:upfuzz (main*) $ ls failure/failure_0/
archive.tar.gz  errorLog  fullSequence_121.report  oriconfig  upconfig
```
### Debug

#### Check container
If the tool runs into problems, you can enter the container to check the log.

```bash
➜  upfuzz git:(main) ✗ docker ps   # get container id
➜  upfuzz git:(main) ✗ bin/en.sh CONTAINERID
root@1c8e314a12a9:/# supervisorctl
sshd                             RUNNING   pid 8, uptime 0:01:03
upfuzz_cassandra:cassandra       RUNNING   pid 9, uptime 0:01:03
upfuzz_cassandra:cqlsh           RUNNING   pid 10, uptime 0:01:03
```

There could be several reasons (1) System starts up but the daemon in container cannot start up (2) The target system cannot start up due to configuration problem or jacoco agent instrumentation problem.

#### OOM
Check memory usage of fuzzing server
```bash
cat /proc/$(pgrep -f "upfuzz_server")/status | grep Vm
# if it's killed by system, use dmsg to check the reason
```

### JACOCO
- If jacoco jar is modified, make sure put the runtime jar into the compile/ so that it can be put into the container.
- Make sure the old jacoco jars are removed in `dependencies` folder.
- If we want to test with functions with weight, use diffchecker to generate a diff_func.txt and put it in the cassandra folder, like `Cassandra-2.2.8/diff_func.txt`.


The two jacoco jars are
* org.jacoco.core-1c01d8328d.jar
* org.jacoco.agent-1c01d8328d-runtime.jar

Use `dependencies/org.jacoco.agent-1c01d8328d-runtime.jar` to replace all `org.jacoco.agent.rt.jar`

### Distributed testing mode
FuzzingServer config.json: listening to all IPs
```json
"serverHost" : "0.0.0.0",
"configDir" : "/PATH/TO/SHARED_FOLDER/",
```

FuzzingClient config.json
```json
"serverHost" : "x.x.x.x",
"configDir" : "/PATH/TO/SHARED_FOLDER/",
```
Set up a shared folder (NFS) between servers to share the configuration file.

### Speed up Cassandra start up

Cassandra by default performs ring delay and gossip wait, but we can skip them if we
only test single node
```bash
# Modify bin/cassandra, add the following code
cassandra_parms="$cassandra_parms -Dcassandra.ring_delay_ms=1 -Dcassandra.skip_wait_for_gossip_to_settle=0"
```

### Process failure reports
```bash
# Aggregate the failure logs: python3 proc_failure.py system
python3 proc_failure.py cassandra
# Read the failure stat
python3 proc_failure.py read
```

### Add cassandra log config for 2.2.x, 3.0.x (3.0.15)
Old version cassandra cannot use env var to adjust log dir, so we add a few scripts to save log separately.
```bash
if [ -z "$CASSANDRA_LOG_DIR" ]; then
  CASSANDRA_LOG_DIR=$CASSANDRA_HOME/logs
fi

launch_service()
{
    pidpath="$1"
    foreground="$2"
    props="$3"
    class="$4"
    cassandra_parms="-Dlogback.configurationFile=logback.xml"
    cassandra_parms="$cassandra_parms -Dcassandra.logdir=$CASSANDRA_LOG_DIR"
```

### Cassandra-5.0
```bash
# When testing cassandra-5.0-rc2, we need to modify the cqlshmain.py to avoid FP.
 vim pylib/cqlshlib/cqlshmain.py
 # Remove the following 2 lines
if baseversion != build_version:
    print("WARNING: cqlsh was built against {}, but this server is {}.  All features may not work!".format(build_version, baseversion))
```

### HBase daemon
daemon
* hbase version >= 2.4.0, use hbase_daemon3.py
* hbase version < 2.4.0, use hbase_daemon2.py

Avoid FP: disable `list_snapshots` command in `hbase_config.json` when upgrading across the formats.
* 2.5.9/3.x/4.x: list_snapshots in format2 (with TTL)
* 2.4.18/2.6.0: list_snapshots in format1 (without TTL)

2.10.2 Script
```bash
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/elif \[ \"\$COMMAND\" = \"dfs\" \] ; then/elif [ \"\$COMMAND\" = \"dfsdaemon\" ] ; then\n  CLASS=org.apache.hadoop.fs.FsShellDaemon\n  HADOOP_OPTS=\"\$HADOOP_OPTS \$HADOOP_CLIENT_OPTS\"\n&/" bin/hdfs
```

3.2.4/3.3.6/3.4.0/3.4.1 Script
```bash
/usr/lib/jvm/java-8-openjdk-amd64/bin/javac -d . -cp "share/hadoop/hdfs/*:share/hadoop/common/*:share/hadoop/common/lib/*" FsShellDaemon.java
sed -i "s/  case \${subcmd} in/&\n    dfsdaemon)\n      HADOOP_CLASSNAME=\"org.apache.hadoop.fs.FsShellDaemon\"\n    ;;/" bin/hdfs
```


### Cassandra daemon
* [cqlsh_daemon2_1.py](src/main/resources/cqlsh_daemon2_1.py): 2.1
* [cqlsh_daemon2.py](src/main/resources/cqlsh_daemon2.py): 2.2.8, 3.0.(15|16|17|30), 3.11.16, 4.0.0
* [cqlsh_daemon3.py](src/main/resources/cqlsh_daemon3.py): N/A
* [cqlsh_daemon4.py](src/main/resources/cqlsh_daemon4.py): 4.0.5, 4.0.12, 4.1.0, 4.1.4
* [cqlsh_daemon5.py](src/main/resources/cqlsh_daemon5.py): 5.0-beta

### HDFS daemon
* FsShellDaemon2.java: hadoop-2.10.2
* FsShellDaemon3.java: (> 3): 3.2.4, 3.3.0
* FsShellDaemon_trunk.java: (>=3.3.4) hadoop-3.3.6, 3.4.0
