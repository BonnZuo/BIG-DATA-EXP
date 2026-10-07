#!/usr/bin/env bash
# 教学命令清单，建议逐行执行和截图。写 /usr、/ 需要 sudo。
# 先确认 /tmp/a /tmp/a1 /tmp/test /tmp/hello /usr/bashrc1 /usr/test /usr/test2 /test /test.tar.gz 不存在。
# 不要把整份脚本当作清理脚本运行。
cd /usr/local
cd ..
cd ~
ls -a /usr
cd /tmp
mkdir a
ls -l /tmp
mkdir -p a1/a2/a3/a4
rmdir a
rmdir -p a1/a2/a3/a4
ls -l /tmp
sudo cp ~/.bashrc /usr/bashrc1
mkdir /tmp/test
sudo cp -r /tmp/test /usr/
sudo mv /usr/bashrc1 /usr/test/
sudo mv /usr/test /usr/test2
sudo rm /usr/test2/bashrc1
sudo rmdir /usr/test2
cat ~/.bashrc
tac ~/.bashrc
more ~/.bashrc
head -n 20 ~/.bashrc
head -n -50 ~/.bashrc
tail -n 20 ~/.bashrc
tail -n +51 ~/.bashrc
touch /tmp/hello
stat /tmp/hello
touch -d '5 days ago' /tmp/hello
stat /tmp/hello
sudo chown root /tmp/hello
ls -l /tmp/hello
find ~ -type f -name .bashrc
sudo mkdir /test
sudo tar -czf /test.tar.gz -C / test
sudo tar -xzf /test.tar.gz -C /tmp
grep -n 'examples' ~/.bashrc  # 没有匹配时退出码 1 是正常结果
# 以 hadoop 用户登录后，启动已配置的集群，不重新格式化 NameNode。
# start-dfs.sh; start-yarn.sh; jps
# hdfs dfs -mkdir -p /user/hadoop/test
# hdfs dfs -ls /user/hadoop
# hdfs dfs -put ~/.bashrc /user/hadoop/test/
# hdfs dfs -ls /user/hadoop/test
# hdfs dfs -get /user/hadoop/test /usr/local/hadoop/  # 目标需可写且尚无 test
