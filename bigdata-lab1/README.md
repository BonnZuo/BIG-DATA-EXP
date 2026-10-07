# 实验一 大数据系统基本实验

包含 Linux 清单、HDFS Java/Shell 对照实现和三个真正使用 Hadoop MapReduce API 的作业。报告为待填写、待集群实测版。当前交付环境无 javac、Hadoop 和 Maven，未进行 Java 编译或集群运行；Python 参考结果不能替代 Hadoop 证据。

## 环境准备
建议 Linux、JDK 8 或 11、Hadoop 3.3.6。须已配置 HDFS 和 YARN，HADOOP_CONF_DIR 指向实际配置，fs.defaultFS 为 hdfs:// 地址。不要在已有集群重新执行 namenode -format。新建伪分布式集群可参考官方 SingleCluster 指南。Maven 可选，使用系统 Hadoop classpath 即可编译。

```bash
export HADOOP_HOME=/usr/local/hadoop
export PATH="$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH"
export HADOOP_CONF_DIR="$HADOOP_HOME/etc/hadoop"
java -version
javac -version
hadoop version
hdfs getconf -confKey fs.defaultFS
jps
hdfs dfs -ls /
# 若由你管理且尚未启动：start-dfs.sh; start-yarn.sh
bash scripts/build.sh
# 也可 mvn -Dhadoop.version=3.3.6 package
bash scripts/run_mapreduce.sh 2>&1 | tee results-mapreduce.log
```

run_mapreduce.sh 创建独立 HDFS 目录，保留输出，不删除已有 HDFS 数据。本地 results 中同名结果已存在时，get 会失败；第二次运行前将旧 results 重命名归档。

## HDFS 十项功能
Java 通用前缀：`hadoop jar build/lab1.jar lab.Main hdfs`。
Shell 通用前缀：`bash scripts/hdfs_shell.sh`。二者后面的参数完全一致。

```bash
upload data/dedup/A.txt /user/hadoop/lab1/demo.txt overwrite
upload data/dedup/B.txt /user/hadoop/lab1/demo.txt append
download /user/hadoop/lab1/demo.txt downloads
cat /user/hadoop/lab1/demo.txt
stat /user/hadoop/lab1/demo.txt
list /user/hadoop/lab1
create-file /user/hadoop/lab1/new/empty.txt
delete-file /user/hadoop/lab1/new/empty.txt
mkdir /user/hadoop/lab1/empty/sub
rmdir /user/hadoop/lab1/empty/sub
append /user/hadoop/lab1/demo.txt head $'HEADER\n'
append /user/hadoop/lab1/demo.txt tail $'TAIL\n'
move /user/hadoop/lab1/demo.txt /user/hadoop/lab1/moved.txt
delete-file /user/hadoop/lab1/moved.txt
```

例如完整调用 `hadoop jar build/lab1.jar lab.Main hdfs cat /user/hadoop/lab1/demo.txt`。
已有文件 upload 不传 mode 时询问追加或覆盖。下载自动选择 filename、filename.1 等空闲名称；Java 用 CREATE_NEW 避免覆盖。rmdir 拒绝删除非空目录。head 追加必须重写文件；Java 通过同目录临时文件及备份回滚替换，适用于教学单写者场景，不保证并发事务、ACL/所有者等完整元数据保留。Shell head 下载重组再覆盖，失败保留恢复副本。FileStatus 不提供创建时间，明确输出修改时间和访问时间；创建时间需求需另建审计记录，不能冒充 mtime。

## MapReduce 原理与结果
- dedup：行按空白分隔归一化为 tab 后作为 Text key，Reducer 每个 key 输出一次。适合题中日期和字符两列。
- sort：LongWritable 数值 key，Shuffle 数值排序，单 Reducer 全局计数，重复整数保留。不要改为多个 Reducer，否则不保证全局名次。
- family：child-parent 自连接；以中间人物为 key，将 child 记录标 C、parent 记录标 P，Reducer 做孩子与父母的笛卡尔积。
- expected 是独立参考答案；family 的行序无语义，核验集合。

```bash
python3 scripts/local_reference.py
python3 scripts/check_results.py results/reference
```

## 取证和提交
逐项运行 scripts/linux_commands.sh 中的教学命令，先确认其指定临时路径未占用。保留 Linux 命令、jps、HDFS Java 与 Shell 的输出截图。至少测试：上传追加与覆盖、重复下载不覆盖、非空目录删除拒绝、首尾追加、移动与删除、三作业成功状态及输出比对。

报告个人信息、真实硬件版本、GitHub 链接、集群结果需补充。把源码和数据上传你自己的公开 GitHub 仓库，再将链接写进报告；不要上传凭据或个人 .bashrc 内容。按“学号-姓名-实验一.pdf”命名，在 https://h.sdu-ai.media/ 的“大数据管理与分析”课程提交。尚未替用户创建账户、发布仓库或提交作业。

官方参考：https://hadoop.apache.org/docs/r3.3.6/hadoop-project-dist/hadoop-common/SingleCluster.html
Shell：https://hadoop.apache.org/docs/r3.3.6/hadoop-project-dist/hadoop-common/FileSystemShell.html
API：https://hadoop.apache.org/docs/r3.3.6/api/org/apache/hadoop/fs/FileSystem.html
MapReduce：https://hadoop.apache.org/docs/r3.3.6/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html
