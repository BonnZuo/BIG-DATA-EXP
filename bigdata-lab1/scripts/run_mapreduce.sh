#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base="/user/$(id -un)/lab1-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p results
printf '%s\n' "$base" > results/hdfs-base.txt
hdfs dfs -mkdir -p "$base/input"
for task in dedup sort family; do
  hdfs dfs -put "data/$task" "$base/input/$task"
  hadoop jar build/lab1.jar lab.Main mr "$task" "$base/input/$task" "$base/output/$task"
  hdfs dfs -get "$base/output/$task/part-r-00000" "results/$task.txt"
done
python3 scripts/check_results.py results
