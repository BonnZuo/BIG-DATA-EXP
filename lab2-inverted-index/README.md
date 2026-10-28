# 实验二 文档倒排索引算法实现

使用 Java MapReduce 为 10 篇公开英文文档构建倒排索引，输出各文档词频及总词频。实验在 Ubuntu 26.04.1 LTS ARM64、OpenJDK 8、Hadoop 3.3.6 单节点伪分布式环境完成。

## 目录

- `src/InvertedIndex.java`：Mapper、Reducer 与作业入口。
- `data/document01.txt` 至 `document10.txt`：实际运行使用的英文正文。
- `sources.tsv`、`sources.json`：文档标题、来源 URL、下载时间、字节数与 SHA-256。
- `stopwords.txt`：63 个停词，每行一个。
- `scripts/prepare_data.py`：从 Apache Hadoop 3.3.6 官方网站下载并提取正文。
- `scripts/setup_cluster.sh`：生成独立配置、初始化实验 HDFS 并启动服务。
- `scripts/run_experiment.sh`：上传、编译、提交作业、下载结果并校验。
- `scripts/verify.py`：使用 Python 独立统计并逐词逐文档检查输出。
- `results/part-r-00000`：本次实验的完整倒排索引。
- `logs/06-job-full.txt`、`logs/08-verification.txt`：实际运行和校验记录。

## 算法

文本使用 `Locale.ROOT` 转为小写，以 `[a-z]+` 匹配连续英文字母，使用 `HashSet` 过滤停词。停词表通过 Hadoop 分布式缓存提供给 Mapper，文档名从 `FileSplit` 取得。

Mapper 对每个有效词输出 `(word, filename)`。Shuffle 按单词分组；Reducer 用 `TreeMap` 累加各文档次数，按文件名排序输出，并附加全部文档中的总词频。作业使用 1 个 Reducer，不设置 Combiner。

输出格式为：

```text
单词<TAB><文档名,次数>;<文档名,次数>;...;<total,总次数>.
```

本实现记录文档词频，不记录位置，也不进行词干化。

## 校验已提交的结果

只需 Python 3，无需启动 Hadoop：

```bash
cd lab2-inverted-index
python3 scripts/verify.py
```

校验涵盖全部词条、各文档次数、总次数、停词残留以及重复文档项。

## 在 Ubuntu 中重新运行

前提：已安装 Hadoop 3.3.6 至 `$HOME/hadoop`、Python 3，以及 Java 8。当前配置脚本使用 ARM64 路径 `/usr/lib/jvm/java-8-openjdk-arm64`；其他架构需先将脚本中的该路径改为实际 JDK 目录。下载语料还需要 `curl`。该配置用于本地实验环境。

仓库已包含输入数据，可以直接使用。若要重新下载，执行 `python3 scripts/prepare_data.py`，它会更新 `data`、停词表和来源清单。

保留仓库中的原始运行记录后，再启动实验服务并提交新作业：

```bash
cd lab2-inverted-index
archive="archive/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$archive"
mv logs results "$archive/"
bash scripts/setup_cluster.sh
bash scripts/run_experiment.sh
```

`setup_cluster.sh` 在项目内生成 `conf/`、`env.sh` 和 `cluster/`，使用独立的实验 HDFS 数据目录；后续终端操作应先 `source env.sh`。脚本使用 HDFS RPC 19000、HDFS Web 19870、YARN Web 18088、作业历史 Web 19888 等端口，其他 Hadoop 服务应避免端口冲突。启动脚本用于启动尚未运行的服务；服务已运行时可直接提交作业。

运行脚本会生成新的 HDFS 根目录，写入 `results/hdfs-base.txt`；编译打包后提交 10 个 Map、1 个 Reduce，并自动下载和校验结果。重复提交前应归档现有 `logs` 与 `results`。

如需停止服务：

```bash
source env.sh
mapred --daemon stop historyserver
yarn --daemon stop nodemanager
yarn --daemon stop resourcemanager
hdfs --daemon stop datanode
hdfs --daemon stop namenode
```

## 本次实验结果

运行日期：2026-10-28。作业编号：`job_1793156416394_0001`，状态成功。

| 指标 | 数值 |
| --- | ---: |
| 英文输入文档 | 10 |
| 正文总字节数 | 224681 |
| 停词表条目 | 63 |
| 输入记录 | 2849 |
| 过滤停词次数 | 10781 |
| 有效词次 | 24381 |
| 不同索引词 | 2952 |

代表词的总词频为 `data=131`、`hadoop=577`、`mapreduce=190`、`file=377`、`cluster=100`。Python 独立校验对全部词条与文档词频比较通过。

## 数据来源

语料来自 [Apache Hadoop 3.3.6 官方文档](https://hadoop.apache.org/docs/r3.3.6/)，用于课程实验，原文权利归原作者所有。逐篇链接见 `sources.tsv`，对应内容摘要见 `sources.json`。文本提取与分词规则决定了这里的计数结果。
