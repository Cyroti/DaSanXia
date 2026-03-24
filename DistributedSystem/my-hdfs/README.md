# my-hdfs 用户手册

my-hdfs 是一个基于 gRPC 的简化分布式文件系统，包含 3 类进程：
- NameNode：管理元数据
- DataNode：存储块数据
- Client：通过 CLI 读写文件

本手册目标：给出一套可直接执行的使用说明，覆盖单机多进程和多物理机器两种部署方式。

## 1. 环境与目录

### 1.1 环境要求

- JDK 17+
- Maven 3.9+
- Windows PowerShell（用于端口释放）

### 1.2 关键目录

```text
src/main/proto/hdfs.proto                    gRPC 协议
src/main/java/com/example/hdfs/namenode      NameNode 实现
src/main/java/com/example/hdfs/datanode      DataNode 实现
src/main/java/com/example/hdfs/client        Client 与 CLI
data/dn1 data/dn2                            DataNode 本地存储目录
data/fsimage.json                            NameNode 元数据快照文件
```

## 2. 快速开始（本地单机）

### 2.1 需要几个终端

建议 4 个拆分终端：
- T1：DataNode dn1
- T2：DataNode dn2
- T3：NameNode
- T4：Client CLI

前置命令可在任意一个终端先执行一次。

### 2.2 前置命令 1：释放端口

默认端口：50051（NameNode）、50061（dn1）、50062（dn2）。

```powershell
$ports = 50051,50061,50062
foreach ($p in $ports) {
  Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
      Write-Host "Killing process $_ on port $p"
      Stop-Process -Id $_ -Force
    }
}
```

如果出现权限问题，用管理员权限打开 PowerShell 再执行。

### 2.3 前置命令 2：编译

```bash
mvn -q clean compile
```

### 2.4 启动服务

T1（dn1）：

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn1 50061 ./data/dn1" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

T2（dn2）：

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn2 50062 ./data/dn2" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

T3（NameNode）：

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.namenode.NameNodeServer" "-Dexec.args=50051 ./data/fsimage.json dn1=127.0.0.1:50061,dn2=127.0.0.1:50062" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

T4（Client CLI）：

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.client.ClientCli" "-Dexec.args=127.0.0.1:50051" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

## 3. CLI 命令手册

### 3.1 命令列表

| 命令 | 用途 | 示例 |
| --- | --- | --- |
| open <path> <r|w> | 打开文件并返回 fd | open /demo.txt w |
| append <fd> <text> | 追加文本到文件 | append 101 hello world |
| read <fd> | 读取文件全部内容 | read 102 |
| close <fd> | 关闭 fd 并提交元数据 | close 101 |
| exit | 退出 CLI | exit |

### 3.2 写入空格的规则

- append 支持文本中间包含空格。
- 示例：append 101 hello distributed system。
- 由于 CLI 会先 trim 输入，文本首尾空格不会保留。

### 3.3 常用操作示例

```text
open /demo.txt w
append 101 hello world
append 101 this is line 2
close 101

open /demo.txt r
read 102
close 102
```

## 4. 多物理机器部署

### 4.1 示例拓扑

| 机器 | 角色 | 地址 |
| --- | --- | --- |
| A | NameNode | 10.0.0.10:50051 |
| B | DataNode dn1 | 10.0.0.11:50061 |
| C | DataNode dn2 | 10.0.0.12:50062 |
| D | Client CLI | 连接 10.0.0.10:50051 |

### 4.2 前置要求

- 每台机器安装 JDK 17+、Maven 3.9+。
- 每台机器都有同版本代码。
- 防火墙放通：A=50051，B=50061，C=50062。
- 每台机器各自执行一次编译：mvn -q clean compile。

### 4.3 启动顺序

1. 在 B 启动 dn1

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn1 50061 ./data/dn1" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

2. 在 C 启动 dn2

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn2 50062 ./data/dn2" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

3. 在 A 启动 NameNode（必须填写 DataNode 的真实 IP）

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.namenode.NameNodeServer" "-Dexec.args=50051 ./data/fsimage.json dn1=10.0.0.11:50061,dn2=10.0.0.12:50062" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

4. 在 D 启动 Client

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.client.ClientCli" "-Dexec.args=10.0.0.10:50051" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

## 5. 源码原理简析

### 5.1 NameNode

- MetadataManager 维护文件与块元数据。
- open(path, mode) 执行读写约束：同文件写互斥，读可并发。
- allocateBlock 进行块分配，并做基础轮询负载分散。
- close 成功后通过 FsImageStore 持久化到 data/fsimage.json。

### 5.2 DataNode

- DataNodeStorage 负责块文件读写。
- 每个块是一个 blockId.blk 文件。
- append 只允许末尾追加，单块最大 4096 字节。
- read 返回整块字节给客户端。

### 5.3 Client

- SimpleHdfsClient 负责所有 RPC 与本地会话状态。
- open 成功后登记本地 fd 到 openFiles。
- append 依据尾块剩余空间切分写入，不够则向 NameNode 申请新块。
- read 按块顺序读取并拼接。
- close 通知 NameNode 并清理本地 fd。

### 5.4 fd 为什么从 101 开始

- fd 由客户端本地生成，不是 NameNode 下发。
- fdGenerator 初始值是 100，open 时先自增再返回。
- 因此同一个客户端进程第一次 open 通常是 101。
- 重启 CLI 后，fd 重新从 101 开始。

## 6. 故障排查

### 6.1 端口占用

- 现象：服务启动失败，提示 bind 或端口已占用。
- 处理：执行 2.2 端口释放命令后重启。

### 6.2 依赖缺失或类找不到

- 现象：NoClassDefFoundError。
- 处理：不要用 java -cp 直接跑；使用 README 中的 mvn exec:java 命令。

### 6.3 改了代码后行为异常

- 处理顺序：mvn -q clean compile，再重新启动 NameNode 与 DataNode。

## 7. 测试

```bash
mvn test
```

自动化测试覆盖写互斥、跨块追加、读写模式约束、close 后持久化等关键行为。
