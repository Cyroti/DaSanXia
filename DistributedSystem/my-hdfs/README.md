# my-hdfs-sim（分块 + 多副本 + RESTful）

本项目是一个面向课程的 HDFS 模拟系统，目标是以良好的软件工程方式实现：

1. 文件按 Block 分块存储
2. 每个 Block 具备多副本
3. NameNode 统一管理并持久化元数据
4. 对外提供清晰的 RESTful 接口

## 核心能力

1. 分块（Blocks）
- 客户端写入文件时，先向 NameNode 申请 block 分配计划
- 按 `blockSize` 将内容拆分为多个 block

2. 多副本（Replication）
- 每个 block 根据 `replication` 分配多个 DataNode
- 写入采用链式副本传播（block 级 pipeline）

3. 元数据管理（NameNode）
- 元数据包含：文件大小、块大小、副本数、block 列表、副本位置
- 元数据落盘到 `data/namenode/metadata.json`

4. 一致性模式
- `SYNC`：链路上全部副本写入成功后才返回
- `ASYNC_OBSERVE`：首副本落盘后立即返回，后续后台传播，便于观察不一致窗口

## 工程结构

- `src/main/java/edu/course/myhdfs/NameNodeServer.java`：元数据服务
- `src/main/java/edu/course/myhdfs/DataNodeServer.java`：Block 存储与复制服务
- `src/main/java/edu/course/myhdfs/ClientCli.java`：命令行客户端
- `src/main/java/edu/course/myhdfs/NameNodeMetadataStore.java`：元数据持久化
- `scripts/start_cluster.sh`：一键启动集群
- `scripts/stop_cluster.sh`：一键停止集群

## RESTful API

### NameNode

1. `GET /api/v1/health`
2. `GET /api/v1/datanodes`
3. `PUT /api/v1/files/allocate`
4. `PUT /api/v1/files/commit`
5. `GET /api/v1/files`
6. `GET /api/v1/files/{file}`

### DataNode

1. `GET /api/v1/health`
2. `GET /api/v1/state`
3. `PUT /api/v1/blocks/{blockId}`
4. `GET /api/v1/blocks/{blockId}?file={file}`

## 快速开始

### 1) 构建

```bash
mvn -q -DskipTests compile
```

### 2) 启动

```bash
chmod +x scripts/*.sh
./scripts/start_cluster.sh
```

### 3) 写入文件（分块 + 3 副本 + 同步）

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='put reportA hello_distributed_system 8 3 sync' exec:java
```

参数顺序：
- `put <file> <content> [blockSize] [replication] [sync|async_observe]`

### 4) 读取并重组文件

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='get reportA' exec:java
```

### 5) 查看块在各副本上的状态

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='inspect reportA' exec:java
```

### 6) 一键演示不一致窗口

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='demo reportB hello_async_replication' exec:java
```

### 7) 停止

```bash
./scripts/stop_cluster.sh
```

## 说明

这是课程实验系统，强调机制与可观测性，不追求生产级 HA。后续可继续增强：

1. NameNode 主备与故障切换
2. 心跳与副本重平衡
3. 校验和与损坏块修复

## Lab1 对照材料

已根据课程 Lab1 目录整理了对照文档，可直接用于写报告与准备提交：

- LAB1_对照与提交清单.md
