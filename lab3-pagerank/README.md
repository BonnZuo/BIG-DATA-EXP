# 实验三 HBase 操作与 PageRank

实验包括两部分。先用 HBase Shell 和 Java API 导入并操作 Student、Course 和 SC，再对同一份链接图分别用 Hadoop MapReduce 与 Spark RDD 实现 PageRank。

## HBase

`data/student.tsv`、`data/course.tsv` 和 `data/sc.tsv` 保留题面数据。Student 用姓名作行键，Course 用课程号，SC 用 `学号#课程号`。题面为 Mary 和 Lisi 都给出 2015003，且 SC 含有 2015002。HBase 导入保留这些原值。Score 列族中的成绩操作使用题面能明确匹配的 Zhangsan 记录。

Java API 与 HBase Shell 使用不同的 `lab3_java_`、`lab3_shell_` 表名，各自验证建表（包括重建）、列出表、整表扫描、列族和列限定符的增删、改值、删行、统计及清空。Student、Course、SC 初次导入分别为 3、3、6 行。Java 的列扫描逐行输出，缺失列显示 `null`。`modifyData` 的三参数版本从终端读取新值，四参数版本可在脚本中直接指定新值。

安装 [Apache HBase 2.5.16 Hadoop 3 发行包](https://hbase.apache.org/downloads/) 和 JDK 8，设置 `HBASE_HOME` 和 `JAVA_HOME` 为实际安装路径后运行：

```bash
bash scripts/run_hbase.sh
```

脚本会创建独立的本地 HBase 实例，数据写入项目的 `build/`，任务结束后停止该实例。重复执行会重建本实验的示例表。Shell 和 Java 的运行日志在 `logs/hbase-shell.log` 和 `logs/hbase-java.log`。

## PageRank

`data/graph.tsv` 为“源网页 + Tab + 逗号分隔出链”的邻接表。E 有显式空出链，G 只有入边，F 无入边。驱动程序读取小型输入图并补齐只在出链中出现的网页。初值为 1/N，阻尼系数为 0.85，迭代 10 次。每轮把悬挂页（无出链网页）的上一轮分数均匀分给所有网页。

- MapReduce 每轮提交一个 YARN 作业。Mapper 发出邻接表和入链贡献，Reducer 保留结构并更新分数。Reducer 用计数器传递下一轮悬挂质量。
- Spark 在同一虚拟机的 Standalone Master 和 Worker 上运行。缓存邻接 RDD，连接分数后用 `flatMapToPair` 和 `reduceByKey` 累加贡献，再以邻接表左连接确保保留所有网页。
- 两套程序每行输出 `(网页,PR值)`，分数保留 10 位小数。验证脚本用 Python Decimal 和稠密转移计算独立核对。

### 运行

准备 JDK 8、Hadoop 3.3.6 单节点 HDFS/YARN 和 Spark 3.5.8。脚本默认读取实验二的 `~/lab2-inverted-index/env.sh`，可设置 `HADOOP_ENV` 指向包含 Hadoop 环境变量的脚本。先启动 HDFS 和 YARN。设置 `SPARK_HOME` 为 Spark 根目录（含 `bin/` 与 `jars/`，也可使用 PySpark 3.5.8 源码包解压后的 `deps/`）。

```bash
bash scripts/run_mr.sh
bash scripts/run_spark.sh
python3 scripts/verify_pagerank.py
```

两步使用相同的 HDFS 输入。`run_mr.sh` 每次创建新的实验路径；`run_spark.sh` 使用该次输入，启动实验专用 Spark 服务并在退出时停止。最终结果分别保存到 `results/mapreduce.txt` 和 `results/spark.txt`。Python 校验只读取仓库结果和原始图，不需要启动 Hadoop 或 Spark。

## 查看虚拟机中的实验记录

进入虚拟机终端后，每次运行一条并截图：

```bash
cd ~/lab3-pagerank
clear && bash scripts/show_evidence.sh hbase-shell
clear && bash scripts/show_evidence.sh hbase-java
clear && bash scripts/show_evidence.sh mr
clear && bash scripts/show_evidence.sh spark
```

截图命令从本次实验的运行记录中提取重点，可在服务停止后运行。完整输出可查看相应的 `logs/` 文件。
