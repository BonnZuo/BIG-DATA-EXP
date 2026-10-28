#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source env.sh
mkdir -p logs results build
runid="$(date +%Y%m%d-%H%M%S)"
base="/user/bonn-zuo/lab2-$runid"
printf '%s\n' "$base" > results/hdfs-base.txt
{
 echo '$ date -Is; hostname; uname -sm; java -version; hadoop version; jps'
 date -Is
 hostname
 uname -sm
 java -version
 hadoop version | head -3
 jps
 echo '$ yarn node -list'
 yarn node -list
} > logs/01-environment.txt 2>&1
{
 echo '$ ls -lh data/*.txt; wc -w data/*.txt'
 ls -lh data/*.txt
 wc -w data/*.txt
 echo '$ cut -f1,2 sources.tsv'
 cut -f1,2 sources.tsv
} > logs/02-documents.txt 2>&1
{
 echo '$ wc -l stopwords.txt; paste -sd " " stopwords.txt'
 wc -l stopwords.txt
 paste -sd ' ' stopwords.txt
 echo '$ head -8 data/document01.txt'
 head -8 data/document01.txt
 echo 'Tokenizer: [a-z]+; lowercase: Locale.ROOT; stopwords: HashSet'
} > logs/03-stopwords.txt 2>&1
{
 echo '$ hdfs dfs -mkdir -p INPUT CONFIG; hdfs dfs -put data/*.txt INPUT/'
 hdfs dfs -mkdir -p "$base/input" "$base/config"
 hdfs dfs -put data/*.txt "$base/input/"
 hdfs dfs -put stopwords.txt "$base/config/"
 echo "$base"
 echo '$ hdfs dfs -ls INPUT'
 hdfs dfs -ls "$base/input"
} > logs/04-hdfs.txt 2>&1
{
 echo '$ javac -encoding UTF-8 -cp "$(hadoop classpath --glob)" -d build src/InvertedIndex.java'
 javac -encoding UTF-8 -cp "$(hadoop classpath --glob)" -d build src/InvertedIndex.java
 echo '$ jar cf inverted-index.jar -C build .; jar tf inverted-index.jar'
 jar cf inverted-index.jar -C build .
 jar tf inverted-index.jar
 echo 'Compilation and packaging completed (exit 0).'
} > logs/05-build.txt 2>&1
printf '$ hadoop jar inverted-index.jar InvertedIndex %s/input %s/output %s/config/stopwords.txt\n' "$base" "$base" "$base" > logs/06-job-full.txt
hadoop jar inverted-index.jar InvertedIndex "$base/input" "$base/output" "$base/config/stopwords.txt" >> logs/06-job-full.txt 2>&1
hdfs dfs -get "$base/output/part-r-00000" results/part-r-00000
{
 echo '$ hdfs dfs -ls OUTPUT'
 hdfs dfs -ls "$base/output"
 echo '$ hdfs dfs -cat OUTPUT/part-r-00000 | head -8'
 head -8 results/part-r-00000
 echo '$ selected words: cluster, data, file, hadoop, mapreduce'
 awk -F '\t' '$1=="cluster" || $1=="data" || $1=="file" || $1=="hadoop" || $1=="mapreduce"' results/part-r-00000
} > logs/07-results.txt 2>&1
python3 scripts/verify.py > logs/08-verification.txt 2>&1
cat logs/08-verification.txt
