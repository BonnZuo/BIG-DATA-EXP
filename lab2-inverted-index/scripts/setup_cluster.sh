#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
mkdir -p conf logs results build cluster
if [ ! -f conf/lab2-configured ]; then
 cp "$HOME/hadoop/etc/hadoop/"* conf/ 2>/dev/null || true
 ROOT="$ROOT" python3 - <<'PY'
import os
from pathlib import Path
import xml.etree.ElementTree as E
r=Path(os.environ['ROOT']); conf=r/'conf'
settings={
'core-site.xml':{'fs.defaultFS':'hdfs://localhost:19000','hadoop.tmp.dir':str(r/'cluster/tmp')},
'hdfs-site.xml':{'dfs.replication':'1','dfs.namenode.name.dir':'file://'+str(r/'cluster/name'),'dfs.datanode.data.dir':'file://'+str(r/'cluster/data'),'dfs.namenode.http-address':'0.0.0.0:19870','dfs.datanode.address':'0.0.0.0:19866','dfs.datanode.http.address':'0.0.0.0:19864','dfs.datanode.ipc.address':'0.0.0.0:19867'},
'mapred-site.xml':{'mapreduce.framework.name':'yarn','mapreduce.application.classpath':str(Path.home()/'hadoop/share/hadoop/mapreduce/*')+','+str(Path.home()/'hadoop/share/hadoop/mapreduce/lib/*'),'mapreduce.map.memory.mb':'384','mapreduce.reduce.memory.mb':'384','mapreduce.map.java.opts':'-Xmx256m','mapreduce.reduce.java.opts':'-Xmx256m','yarn.app.mapreduce.am.resource.mb':'512','yarn.app.mapreduce.am.command-opts':'-Xmx320m','mapreduce.job.running.map.limit':'2','mapreduce.jobhistory.address':'localhost:11020','mapreduce.jobhistory.webapp.address':'0.0.0.0:19888'},
'yarn-site.xml':{'yarn.resourcemanager.hostname':'localhost','yarn.resourcemanager.webapp.address':'0.0.0.0:18088','yarn.nodemanager.aux-services':'mapreduce_shuffle','yarn.nodemanager.env-whitelist':'JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_HOME,PATH,LANG,TZ,HADOOP_MAPRED_HOME','yarn.nodemanager.resource.memory-mb':'2048','yarn.nodemanager.resource.cpu-vcores':'2','yarn.scheduler.minimum-allocation-mb':'128','yarn.scheduler.maximum-allocation-mb':'2048','yarn.nodemanager.vmem-check-enabled':'false','yarn.nodemanager.pmem-check-enabled':'false','yarn.nodemanager.local-dirs':str(r/'cluster/nm-local'),'yarn.nodemanager.log-dirs':str(r/'cluster/nm-logs'),'yarn.log-aggregation-enable':'true'}}
for name,props in settings.items():
 root=E.Element('configuration')
 for k,v in props.items():
  p=E.SubElement(root,'property');E.SubElement(p,'name').text=k;E.SubElement(p,'value').text=v
 E.indent(root);E.ElementTree(root).write(conf/name,encoding='unicode')
with (conf/'hadoop-env.sh').open('a') as f:
 f.write('\nexport JAVA_HOME=/usr/lib/jvm/java-8-openjdk-arm64\nexport HADOOP_HEAPSIZE_MAX=256\nexport HADOOP_MAPRED_HOME="$HOME/hadoop"\n')
(conf/'lab2-configured').touch()
PY
fi
cat > env.sh <<EOF
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-arm64
export HADOOP_HOME="$HOME/hadoop"
export HADOOP_CONF_DIR="$ROOT/conf"
export HADOOP_LOG_DIR="$ROOT/logs/daemons"
export HADOOP_PID_DIR="$ROOT/cluster/pids"
export HADOOP_MAPRED_HOME="$HOME/hadoop"
export HADOOP_COMMON_HOME="$HOME/hadoop"
export HADOOP_HDFS_HOME="$HOME/hadoop"
export HADOOP_YARN_HOME="$HOME/hadoop"
export PATH="$HOME/hadoop/bin:\$JAVA_HOME/bin:\$PATH"
export YARN_RESOURCEMANAGER_HEAPSIZE=256
export YARN_NODEMANAGER_HEAPSIZE=256
EOF
source env.sh
mkdir -p "$HADOOP_LOG_DIR" "$HADOOP_PID_DIR"
if [ ! -f cluster/name/current/VERSION ]; then
 hdfs namenode -format -nonInteractive > logs/format.log 2>&1
fi
hdfs --daemon start namenode
hdfs --daemon start datanode
yarn --daemon start resourcemanager
yarn --daemon start nodemanager
mapred --daemon start historyserver
hdfs dfsadmin -safemode wait
jps
