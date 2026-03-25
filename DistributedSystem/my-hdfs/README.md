# my-hdfs 用户手册（主从复制版）

my-hdfs 是一个基于 gRPC 的简化分布式文件系统，包含 3 类进程：

- NameNode：管理元数据（文件、块、副本）
- DataNode：存储块数据
- Client CLI：发起读写请求

当前版本采用基于领导者的复制（leader-based replication）：

- 每个块的首副本为主节点（leader）
- 客户端只能写主节点
- 主节点按顺序同步复制到所有从节点（followers）
- 只有所有副本确认后，写入才算成功

## 1. 环境要求

- JDK 17+
- Maven 3.9+
- Windows PowerShell

## 2. 目录说明

```text
src/main/proto/hdfs.proto                    gRPC 协议
src/main/java/com/example/hdfs/namenode      NameNode 实现
src/main/java/com/example/hdfs/datanode      DataNode 实现（主从复制转发）
src/main/java/com/example/hdfs/client        Client 与 CLI
data/dn1 data/dn2 data/dn3                   DataNode 本地数据目录
data/fsimage.json                            NameNode 元数据快照
```

## 3. 本地启动

建议 5 个终端：

- T1：dn1
- T2：dn2
- T3：dn3
- T4：NameNode
- T5：Client CLI

### 3.1 释放端口

```powershell
$ports = 50051,50061,50062,50063
foreach ($p in $ports) {
  Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
      Write-Host "Killing process $_ on port $p"
      Stop-Process -Id $_ -Force
    }
}
```

### 3.2 编译

```bash
mvn -q generate-sources generate-test-sources test-compile
```

### 3.3 启动服务

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn1 50061 ./data/dn1" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn2 50062 ./data/dn2" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.datanode.DataNodeServer" "-Dexec.args=dn3 50063 ./data/dn3" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.namenode.NameNodeServer" "-Dexec.args=50051 ./data/fsimage.json dn1=127.0.0.1:50061,dn2=127.0.0.1:50062,dn3=127.0.0.1:50063" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

```bash
mvn -q "-Dexec.mainClass=com.example.hdfs.client.ClientCli" "-Dexec.args=127.0.0.1:50051" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

## 4. 机制说明

1. NameNode 分配块时返回 3 个副本地址。
2. Client 只向主副本发起 `append`。
3. 主副本本地写入后，同步向从副本传播同一写入。
4. 任一从副本复制失败，整次写入失败。
5. 从副本拒绝客户端直写请求（只接受主副本复制流）。

## 5. CLI 命令

| 命令 | 用途 | 示例 |
| --- | --- | --- |
| `open [path] [r/w]` | 打开文件返回 fd | `open /demo.txt w` |
| `append [fd] [text]` | 追加文本 | `append 101 hello world` |
| `read [fd]` | 读取当前内容 | `read 102` |
| `close [fd]` | 关闭 fd | `close 101` |
| `check [path]` | 检查副本一致性 | `check /demo.txt` |
| `exit` | 退出 CLI | `exit` |

## 6. 关键参数

- `REPLICATION_FACTOR=3`
- `REQUIRED_REPLICA_ACKS=3`
- `RPC_TIMEOUT_MS=2000`
- `PIPELINE_FORWARD_TIMEOUT_MS=2000`

## 7. 测试

```bash
mvn -q test
```
