#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source "${HADOOP_ENV:-$HOME/lab2-inverted-index/env.sh}"
mkdir -p build/mr logs results
javac -encoding UTF-8 -cp "$(hadoop classpath --glob)" -d build/mr src/{Graph,MapReducePageRank}.java
jar cf build/mr.jar -C build/mr .
base="/user/$USER/lab3-$(date +%Y%m%d-%H%M%S)-$$"
hdfs dfs -mkdir -p "$base/input"
hdfs dfs -put data/graph.tsv "$base/input/"
printf '%s\n' "$base" > results/hdfs-base.txt
hadoop jar build/mr.jar MapReducePageRank "$base/input/graph.tsv" "$base/mr" 2>&1 | tee logs/mr.log
hdfs dfs -cat "$base/mr/result.txt" > results/mapreduce.txt
