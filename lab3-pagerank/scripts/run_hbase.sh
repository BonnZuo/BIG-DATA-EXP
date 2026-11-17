#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-arm64}"
export PATH="$JAVA_HOME/bin:$PATH"
export HBASE_HOME="${HBASE_HOME:-$HOME/lab3-tools/hbase-2.5.16-hadoop3}"
export HBASE_CONF_DIR="$PWD/build/hbase-conf"
export HBASE_LOG_DIR="$PWD/logs"
export HBASE_PID_DIR="$PWD/build/hbase-pids"
export HBASE_HEAPSIZE=384
mkdir -p "$HBASE_CONF_DIR" "$HBASE_PID_DIR" build/hbase-classes logs
cp "$HBASE_HOME/conf/"* "$HBASE_CONF_DIR/"
cat > "$HBASE_CONF_DIR/hbase-site.xml" <<XML
<configuration>
  <property><name>hbase.cluster.distributed</name><value>false</value></property>
  <property><name>hbase.rootdir</name><value>file://$PWD/build/hbase-data</value></property>
  <property><name>hbase.zookeeper.property.dataDir</name><value>$PWD/build/hbase-zk</value></property>
  <property><name>hbase.zookeeper.property.clientPort</name><value>12181</value></property>
  <property><name>hbase.master.info.bindAddress</name><value>127.0.0.1</value></property>
  <property><name>hbase.master.info.port</name><value>16110</value></property>
  <property><name>hbase.regionserver.info.port</name><value>16130</value></property>
  <property><name>hbase.master.port</name><value>16100</value></property>
  <property><name>hbase.zookeeper.property.clientPortAddress</name><value>127.0.0.1</value></property>
  <property><name>hbase.unsafe.stream.capability.enforce</name><value>false</value></property>
</configuration>
XML
"$HBASE_HOME/bin/start-hbase.sh"
trap '"$HBASE_HOME/bin/stop-hbase.sh"' EXIT
javac -encoding UTF-8 -cp "$("$HBASE_HOME/bin/hbase" classpath)" -d build/hbase-classes src/HBaseLab.java
export HBASE_CLASSPATH="$PWD/build/hbase-classes"
"$HBASE_HOME/bin/hbase" HBaseLab 2>&1 | tee logs/hbase-java.log
"$HBASE_HOME/bin/hbase" shell -n scripts/hbase_shell.rb 2>&1 | tee logs/hbase-shell.log
