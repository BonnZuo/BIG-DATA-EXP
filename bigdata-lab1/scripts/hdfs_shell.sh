#!/usr/bin/env bash
set -euo pipefail
op=${1:?operation required}; shift
case "$op" in
upload)
 localfile=$1; dst=$2; mode=${3:-}
 hdfs dfs -mkdir -p "$(dirname "$dst")"
 if hdfs dfs -test -e "$dst"; then
   hdfs dfs -test -f "$dst"
   if [[ -z "$mode" ]]; then read -r -p 'append or overwrite? ' mode; fi
   case "$mode" in append) hdfs dfs -appendToFile "$localfile" "$dst";; overwrite) hdfs dfs -put -f "$localfile" "$dst";; *) exit 2;; esac
 else hdfs dfs -put "$localfile" "$dst"; fi;;
download)
 src=$1; dir=$2; mkdir -p "$dir"; base="$dir/$(basename "$src")"; dst=$base; n=0
 while [[ -e "$dst" ]]; do n=$((n+1)); dst="$base.$n"; done
 hdfs dfs -get "$src" "$dst"; printf '%s\n' "$dst";;
cat) hdfs dfs -cat "$1";;
stat) hdfs dfs -ls "$1"; hdfs dfs -stat 'perm:%A size:%b mtime:%y atime:%x name:%n' "$1";;
list) hdfs dfs -ls -R "$1";;
create-file) hdfs dfs -mkdir -p "$(dirname "$1")"; if hdfs dfs -test -e "$1"; then echo 'already exists' >&2; exit 1; fi; hdfs dfs -touchz "$1";;
mkdir) hdfs dfs -mkdir -p "$1";;
rmdir) hdfs dfs -rmdir "$1";;
append)
 dst=$1; mode=$2; content=$3; hdfs dfs -test -f "$dst"
 if [[ "$mode" == tail ]]; then printf '%s' "$content" | hdfs dfs -appendToFile - "$dst"
 elif [[ "$mode" == head ]]; then
   work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
   hdfs dfs -get "$dst" "$work/original"
   { printf '%s' "$content"; cat "$work/original"; } > "$work/new"
   # 教学单用户场景：put -f 覆盖；失败时保留本地恢复副本。
   if ! hdfs dfs -put -f "$work/new" "$dst"; then trap - EXIT; echo "Recovery copy: $work/original" >&2; exit 1; fi
 else exit 2; fi;;
delete-file) hdfs dfs -test -f "$1"; hdfs dfs -rm "$1";;
move) hdfs dfs -test -f "$1"; if hdfs dfs -test -e "$2"; then echo 'destination exists' >&2; exit 1; fi; hdfs dfs -mkdir -p "$(dirname "$2")"; hdfs dfs -mv "$1" "$2";;
*) echo 'unknown operation' >&2; exit 2;;
esac
