# my-hdfs-sim（课程项目版）

这是一个面向分布式系统课程的 HDFS 数据流模拟项目，核心目标是：

- 支持 1 个 NameNode + 3 个及以上 DataNode
- 使用 pipeline 方式进行副本传播
- 同时支持“强一致写入”与“可观测不一致”实验模式

## 项目结构（已去除嵌套目录）

当前项目根目录就是 `my-hdfs`，不再使用 `pj/DistributedSystem` 这种嵌套形式。

- `src/main/java/edu/course/myhdfs`：Java 源码
- `scripts`：启动/停止脚本
- `data`：各 DataNode 本地落盘目录
- `logs`：运行日志

## 架构说明

角色划分：

1. NameNode：返回元数据，指定主副本（Primary）
2. DataNode：保存数据并按 pipeline 链路继续转发
3. Client：通过命令行发起写入和读取

写入链路（pipeline）：

1. Client 写到主副本 `dn1`
2. `dn1` 转发到 `dn2`
3. `dn2` 转发到 `dn3`
4. 直到尾节点

## 一致性模式

### 1) `SYNC`（同步复制）

- 主副本只有在整条 pipeline 都成功后才返回成功
- 成功返回后可认为各副本已一致
- 适合讨论顺序一致性语义

### 2) `ASYNC_OBSERVE`（实验观察模式）

- 主副本本地落盘后立即返回
- 后续副本在后台继续传播
- 客户端可在短时间窗口读到不一致状态

## 如何放大不一致现象

每个 DataNode 支持两类参数：

- `forwardDelayMs`：转发固定延迟
- `throttleBytesPerSec`：按带宽限速

两者叠加后，复制窗口会变大，更容易观察到“先不一致、后收敛”。

## 构建与运行

### 1) 构建

```bash
mvn -q -DskipTests compile
```

### 2) 启动集群

```bash
chmod +x scripts/*.sh
./scripts/start_cluster.sh
```

默认端口：

- NameNode：`127.0.0.1:9000`
- DataNode1：`127.0.0.1:9001`
- DataNode2：`127.0.0.1:9002`
- DataNode3：`127.0.0.1:9003`

### 3) 停止集群

```bash
./scripts/stop_cluster.sh
```

## 客户端命令

### 同步写入（写成功即全副本一致）

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='write report_sync value_sync sync' exec:java
```

### 读取所有副本

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='readall report_sync' exec:java
```

### 不一致性演示

```bash
mvn -q -Dexec.mainClass=edu.course.myhdfs.ClientCli \
  -Dexec.args='demo report_async hello_async' exec:java
```

`demo` 会自动执行：

1. 异步写入
2. 立刻读取所有副本（应看到不一致）
3. 等待一段时间
4. 再次读取（应看到收敛一致）

## HTTP 接口

NameNode：

- `GET /allocate?file=<name>`
- `GET /replicas`

DataNode：

- `PUT /write?file=<name>`（客户端入口）
- `PUT /replicate?file=<name>`（节点间转发）
- `GET /read?file=<name>`
- `GET /state`
- `GET /health`

写入请求头：

- `X-Consistency-Mode: SYNC | ASYNC_OBSERVE`
- `X-Client-Id: <id>`
- `X-Seq: <seq>`

## 说明

这是课程实验项目，不是生产级 HDFS。若要继续增强，可加入：

- 选主与故障切换（epoch/term）
- Quorum 读写策略
- 校验与反熵修复机制
