# 设计说明（分块 + 多副本 + NameNode 元数据）

## 1. 设计目标

本次实现重点解决三个问题：

1. 文件分块：支持把一个文件切分为多个 Block
2. 多副本：每个 Block 复制到多个 DataNode
3. 元数据集中管理：NameNode 统一维护文件到 Block 到副本位置的映射

同时保持 RESTful 接口风格，便于后续扩展和自动化测试。

## 2. 关键架构

### 2.1 NameNode（控制面）

职责：

1. 分配 block 计划（`allocate`）
2. 接收提交并持久化元数据（`commit`）
3. 提供文件查询与列表接口

元数据内容：

1. 文件级：`fileSizeBytes`、`blockSize`、`replication`
2. 块级：`blockId`、`index`、`sizeBytes`
3. 副本级：每个 block 对应多个 `ReplicaInfo`

持久化文件：`data/namenode/metadata.json`

### 2.2 DataNode（数据面）

职责：

1. 按 block 写入/读取数据
2. 根据请求头中的副本链，向下游 DataNode 继续复制
3. 支持同步与异步两种复制确认模式

### 2.3 Client（编排层）

写流程：

1. 请求 NameNode 生成分块与副本计划
2. 将文件按 blockSize 切分
3. 按 block 把数据写到链首副本（首副本再转发）
4. 全部块写入后向 NameNode 提交元数据

读流程：

1. 从 NameNode 拉取文件元数据
2. 按 block 顺序从副本列表读取
3. 拼接 block 还原完整文件

## 3. 一致性语义

### 3.1 `SYNC`

1. 链首副本会等待整条副本链写入成功后再返回
2. 写入确认时，可认为该 block 副本集合已收敛

### 3.2 `ASYNC_OBSERVE`

1. 链首副本本地落盘后立即返回
2. 下游副本后台继续复制
3. 在短窗口内可观测到副本不一致

## 4. 软件工程实践

本实现遵循以下工程原则：

1. 职责分离
- NameNode 与 DataNode 职责明确分离
- 元数据持久化抽象为 `NameNodeMetadataStore`

2. 接口清晰
- 全部核心能力使用 RESTful API 暴露
- 请求/响应模型集中定义在 `Models`

3. 可观测性
- 启动脚本带健康检查
- `inspect` 命令可直接查看 block 在各副本上的状态

4. 可维护性
- 目录结构统一、命名一致
- 脚本化启动与停止，便于演示与回归验证

## 5. 后续可扩展方向

1. NameNode 主备、心跳与失效转移
2. 副本重平衡与重复制
3. 校验和与坏块修复
4. 更严格的顺序保证与故障恢复日志
