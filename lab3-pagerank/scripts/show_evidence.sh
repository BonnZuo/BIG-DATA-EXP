#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in
  hbase-java)
    printf 'HBase Java API — tables, counts and data operations\n\n'
    awk '/^TABLES|^PASS|^SCAN (family|column)|^Mary[[:space:]]null|^Zhangsan[[:space:]]Math=/' logs/hbase-java.log
    ;;
  hbase-shell)
    printf 'HBase Shell — scans, row counts and table operations\n\n'
    awk '
      /^===/ { stage=1; if ($0 ~ /LIST/) listing=1; else listing=0; print ""; print; next }
      !stage { next }
      /^lab3_shell_[[:alpha:]]+$/ { print; next }
      /^[0-9]+ row\(s\)$/ && listing {
          if (++counts == 3 || counts == 5 || counts == 7) {
              label = counts == 3 ? "Student" : counts == 5 ? "Course" : "SC"
              print label ": " $0
          }
          next
      }
      /^hbase:[0-9]+:0> count |^hbase:[0-9]+:0> (alter|deleteall|truncate) / {
          sub(/^hbase:[0-9]+:0> /, "hbase> "); print; next
      }
      /^=> [0-9]+$/ && stage { print; next }
      /Extra:note timestamp=.*value=lab|column=Score:Math.*value=90/ {
          sub(/timestamp=[^, ]+,? /, ""); if (!seen[$0]++) print
      }
    ' logs/hbase-shell.log
    ;;
  mr)
    printf 'Hadoop MapReduce PageRank — ten YARN rounds\n\n'
    awk '/^MR round=/ { print $1,$2,$3,$4 }' logs/mr.log
    printf '\nRound 10 result\n'
    cat results/mapreduce.txt
    ;;
  spark)
    printf 'Spark Standalone PageRank — ten RDD rounds\n\n'
    awk '/^Spark round=/' logs/spark.log
    printf '\nRound 10 result\n'
    cat results/spark.txt
    printf '\n'
    python3 scripts/verify_pagerank.py
    ;;
  *) printf 'Usage: bash scripts/show_evidence.sh {hbase-java|hbase-shell|mr|spark}\n' >&2; exit 2 ;;
esac
