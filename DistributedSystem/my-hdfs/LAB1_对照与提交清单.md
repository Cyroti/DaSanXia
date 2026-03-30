# 分布式系统概论 Lab1 对照与提交清单

本文用于把老师给出的 Lab1 目录要求，和当前 my-hdfs 实现一一对齐，方便你直接用于报告与验收。

## 1 设计

### 1.1 NameNode

当前实现对应：

- 入口服务：src/main/java/edu/course/myhdfs/NameNodeServer.java
- 元数据持久化：src/main/java/edu/course/myhdfs/NameNodeMetadataStore.java
- 数据模型：src/main/java/edu/course/myhdfs/Models.java

可写入报告的要点：

1. NameNode 提供块分配（allocate）与元数据提交（commit）能力
2. 元数据包含文件、块、副本三层映射关系
3. 元数据持久化到 data/namenode/metadata.json

### 1.2 DataNode

当前实现对应：

- 入口服务：src/main/java/edu/course/myhdfs/DataNodeServer.java

可写入报告的要点：

1. DataNode 以 block 为基本存储单位
2. DataNode 支持多副本链路传播
3. 支持 SYNC 与 ASYNC_OBSERVE 两种复制语义

### 1.3 Client

当前实现对应：

- 客户端：src/main/java/edu/course/myhdfs/ClientCli.java

可写入报告的要点：

1. put：先 allocate，再分块写入，最后 commit
2. get：按 NameNode 元数据顺序重组文件
3. inspect：逐块查看各副本状态
4. demo：演示不一致窗口与收敛

## 2 实现

### 2.1 NameNode

REST 接口：

1. GET /api/v1/health
2. GET /api/v1/datanodes
3. PUT /api/v1/files/allocate
4. PUT /api/v1/files/commit
5. GET /api/v1/files
6. GET /api/v1/files/{file}

### 2.2 DataNode

REST 接口：

1. GET /api/v1/health
2. GET /api/v1/state
3. PUT /api/v1/blocks/{blockId}
4. GET /api/v1/blocks/{blockId}?file={file}

### 2.3 Client

命令：

1. put <file> <content> [blockSize] [replication] [sync|async_observe]
2. get <file>
3. ls
4. inspect <file>
5. demo <file> <content>

### 2.4 Notes

建议在报告强调：

1. 当前项目采用教学友好的 REST + JSON，不引入重型 RPC
2. 当前支持分块、多副本、元数据持久化
3. 当前为课程实验系统，不含 NameNode 主备切换与副本重平衡

## 3 上传

### 3.1 截止日期

按老师通知填写。

### 3.2 提交内容（建议）

1. 源码目录 my-hdfs（不含大体积日志）
2. 实验报告 PDF
3. 运行截图（启动、put/get、inspect、demo）
4. metadata.json 截图（FsImage 证据）

## 4 测试

### 4.1 单元测试（当前状态）

当前项目暂未新增 JUnit 测试类，建议补充最小测试集：

1. block 切分正确性
2. 元数据分配正确性（block 数和副本数）
3. 读重组结果与原文一致

### 4.2 手动测试（可直接执行）

1. 启动集群

```bash
./scripts/start_cluster.sh
```

2. 同步写入并读取

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='put reportA hello_distributed_system 8 3 sync' exec:java
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='get reportA' exec:java
```

3. 查看块副本

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='inspect reportA' exec:java
```

4. 不一致窗口演示

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli -Dexec.args='demo reportB hello_async_replication' exec:java
```

### 4.3 FsImage

课程中可将 NameNode 元数据持久化文件视作简化版 FsImage 证据：

- 文件位置：data/namenode/metadata.json

建议在报告中贴出该文件片段，说明：

1. 文件被切成多少 block
2. 每个 block 的副本放置在哪些 DataNode
3. 文件大小、块大小、副本数

## 附录 A 项目结构（当前）

```text
my-hdfs/
  src/main/java/edu/course/myhdfs/
    NameNodeServer.java
    NameNodeMetadataStore.java
    DataNodeServer.java
    ClientCli.java
    Models.java
    HttpUtil.java
    Jsons.java
  scripts/
    start_cluster.sh
    stop_cluster.sh
  data/
    namenode/metadata.json
```
