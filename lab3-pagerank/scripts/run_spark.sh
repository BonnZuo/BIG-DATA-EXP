#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source "${HADOOP_ENV:-$HOME/lab2-inverted-index/env.sh}"
export SPARK_HOME="${SPARK_HOME:-$HOME/lab3-tools/pyspark-3.5.8/deps}"
export SPARK_LOCAL_IP=127.0.0.1
export SPARK_DAEMON_MEMORY=256m
master=spark://127.0.0.1:17077
mkdir -p build/{spark,spark-worker} logs results
javac -encoding UTF-8 -cp "$SPARK_HOME/jars/*" -d build/spark src/{Graph,SparkPageRank}.java
jar cf build/spark.jar -C build/spark .
master_pid=
worker_pid=
stop_spark() {
    if [[ -n "$worker_pid" ]]; then kill "$worker_pid" 2>/dev/null || true; fi
    if [[ -n "$master_pid" ]]; then kill "$master_pid" 2>/dev/null || true; fi
}
trap stop_spark EXIT
"$SPARK_HOME/bin/spark-class" org.apache.spark.deploy.master.Master \
    --host 127.0.0.1 --port 17077 --webui-port 18080 > logs/spark-master.log 2>&1 &
master_pid=$!
"$SPARK_HOME/bin/spark-class" org.apache.spark.deploy.worker.Worker \
    --host 127.0.0.1 --webui-port 18081 --cores 2 --memory 512m \
    --work-dir "$PWD/build/spark-worker" "$master" > logs/spark-worker.log 2>&1 &
worker_pid=$!
for attempt in {1..40}; do
    if grep -q 'Successfully registered with master' logs/spark-worker.log; then break; fi
    kill -0 "$master_pid" "$worker_pid"
    sleep 1
done
grep -q 'Successfully registered with master' logs/spark-worker.log
base=$(cat results/hdfs-base.txt)
output="$base/spark-$(date +%s)-$$"
"$SPARK_HOME/bin/spark-submit" --master "$master" --class SparkPageRank \
    --driver-memory 512m --executor-memory 512m --total-executor-cores 2 \
    --conf spark.driver.host=127.0.0.1 --conf spark.ui.enabled=false \
    build/spark.jar "$base/input/graph.tsv" "$output" 2>&1 | tee logs/spark.log
hdfs dfs -cat "$output/part-*" > results/spark.txt
python3 scripts/verify_pagerank.py | tee results/verification.txt
