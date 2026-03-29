# 《信息安全》实验报告

| 项目 | 内容 |
| ---- | ---- |
| 实验名称 | DES & AES |
| 姓名 | 黄文峰 |
| 学号 | 23302010049 |
| 日期 | 2026/3/30 |

---

## 1 实验目的

- Implementing DES algorithm
- Understanding usage of AES algorithm

## 2 实验内容

1. 补全 `des_scaffold.py` 中的四个 DES 函数：
   - `permutation_by_table`：按表进行置换
   - `generate_round_keys`：生成 16 轮子密钥
   - `round_function`：轮函数（扩展、S盒替换、置换）
   - `encrypt`：加密/解密主函数（Feistel 网络）
2. 使用补全的 DES 解密指定密文（key: `FudanNiu`）
3. 用 DES 解密结果作为 AES 密钥，解密 `odd.enc` 或 `even.enc`
4. 完成实验报告

## 3 实验环境

- 操作系统：<!-- TODO: 填写你的操作系统 -->
- 操作系统：Ubuntu 24.04.4 LTS (x86_64), Kernel 6.14.0-061400-generic
- 编程语言：Python
- 运行命令：`python des_scaffold.py`
- AES 解密工具：OpenSSL / PyCryptodome

## 4 实验步骤与思路

> 结合代码说明算法设计思想与实现步骤。

### 4.1 permutation_by_table 实现

<!-- TODO: 描述置换函数的设计思路与关键代码 -->

该函数作为所有置换操作的通用实现，支持 IP、IP_INV、PC1、PC2、E、P 等不同置换表复用。核心思路是按置换表逐位取出原始 block 中对应位置的 bit，然后左移拼接得到新整数。

关键实现如下：

```python
def permutation_by_table(block, block_len, table):
   permuted = 0
   for position in table:
      bit = (block >> (block_len - position)) & 0x1
      permuted = (permuted << 1) | bit
   return permuted
```

### 4.2 generate_round_keys 实现

<!-- TODO: 描述子密钥生成的设计思路与关键代码 -->

子密钥生成严格按 DES 标准流程：先对 $C_0,D_0$ 进行 16 轮循环左移，再把每轮的 $C_i||D_i$ 通过 PC2 压缩为 48 bit 子密钥。左移位数使用固定序列 `(1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1)`。

实现上，我先保存每一轮的 $C_i,D_i$，再统一应用 PC2，最后得到 round_keys[1..16]。

### 4.3 round_function 实现

<!-- TODO: 描述轮函数的设计思路与关键代码（扩展E、异或、S盒、置换P） -->

轮函数按照 “扩展 E -> 异或子密钥 -> S 盒 -> P 置换” 执行：

1. 先把 32 bit 的 $R_i$ 扩展到 48 bit。
2. 与本轮 48 bit 子密钥异或。
3. 切成 8 组 6 bit，每组通过对应 S 盒映射为 4 bit。
4. 拼回 32 bit 后再做一次 P 置换输出。

S 盒地址解释采用标准规则：首尾两位构成行号，中间四位构成列号。

### 4.4 encrypt 实现

<!-- TODO: 描述加密/解密主函数的设计思路与关键代码（Feistel网络、IP/IP_INV） -->

encrypt 函数统一实现了 DES 加密/解密主流程：

1. 密钥先经 PC1 置换并拆分为 $C_0,D_0$。
2. 生成 16 轮子密钥。
3. 明文先经过 IP，再拆分为 $L_0,R_0$。
4. 执行 16 轮 Feistel 迭代。
5. 轮结束后拼接为 $R_{16}L_{16}$，经过 IP_INV 得到最终结果。

解密与加密代码共用同一套逻辑，只是将子密钥顺序反向取用（第 16 轮到第 1 轮）。

### 4.5 DES 解密密文

<!-- TODO: 记录解密过程和结果 -->
<!-- 学号尾号奇数：解密 cipher_text_odd = 0x9b99d07d9980305e -->
<!-- 学号尾号偶数：解密 cipher_text_even = 0x6f612748df99a70c -->
<!-- key = FudanNiu -->

我是奇数学号尾号（9），因此解密 `cipher_text_odd`。

参数：

- key: `FudanNiu`
- cipher_text_odd: `0x9b99d07d9980305e`

程序输出结果：

- Plain Text (hex): `577568616e563521`
- Plain Text (utf8/latin1): `WuhanV5!`

因此 DES 解密得到的口令为 `WuhanV5!`。

### 4.6 AES 解密密文

<!-- TODO: 记录使用 DES 解密结果作为 AES 密钥的解密过程和结果 -->
<!-- 学号尾号奇数：解密 odd.enc -->
<!-- 学号尾号偶数：解密 even.enc -->

按实验要求，使用上一步 DES 解出的字符串作为 AES 解密口令。

我执行的命令为：

```bash
openssl enc -d -aes256 -in odd.enc -out odd.plain -pass pass:WuhanV5!
```

解密成功，生成 `odd.plain`，文件长度 593 字节，可正常读出英文明文内容。

## 5 实验结果及分析

> 通过复制或截图的方式记录实验执行的结果。

<!-- TODO: 粘贴 DES 解密结果 -->

DES 自检与解密输出如下：

```text
DES self-check: PASS
Expected: 85e813540f0ab405
Actual  : 85e813540f0ab405
openssl available: True
Plain Text (hex): 577568616e563521
Plain Text (utf8/latin1): WuhanV5!
```

<!-- TODO: 粘贴 AES 解密后的明文内容 -->

AES 解密后的 `odd.plain` 内容开头如下：

```text
After screenshots of his WeChat messages were shared on Chinese forums and gained huge attention,
the supervision department summoned him to talk, where he was blamed for leaking the information.
On 3 January 2020, police from the Wuhan Public Security Bureau investigated the case...
```

<!-- TODO: 简要分析 DES 算法的特点 -->

DES 的实现特点是“位运算 + 表驱动”非常明确，逻辑上易于模块化拆分，但对 bit 索引、拼接顺序和轮密钥顺序非常敏感。工程上最容易出错的是：

1. 置换表的位序是否按标准从 1 开始解释。
2. S 盒行列计算是否正确。
3. 加解密时子密钥顺序是否反向。

本次通过标准测试向量和最终解密结果双重验证，说明实现正确。

## 6 扩展实验（选做，加分项）

> 实现各种加密模式（mode）、3DES，可有适当加分。

<!-- TODO: 如完成扩展实验，在此描述思路、实现与结果 -->

本次已完成拓展实验，实现了 3DES 与 CBC 模式，代码文件为 `23302010049_des_extension.py`。

具体内容：

1. 实现 3DES EDE 流程：`E(K1) -> D(K2) -> E(K3)`，并实现对应逆过程。
2. 实现 CBC 分组模式：每个明文块先与前一密文块（首块与 IV）异或再加密。
3. 实现 PKCS7 填充与去填充，使任意长度明文可处理。
4. 提供自动演示函数 `run_extension_demo()`，输出密文长度、密文片段和回环验证结果。

运行结果（已在本机验证）：

```text
[3DES-CBC Extension Demo]
plaintext bytes: 69
cipher bytes   : 72
cipher hex head: 5065fd7aae6343a21996014f8814309f1e2b1a644e0a072c2d3a331fe1e836b2
roundtrip ok   : True
recovered text : This is lab2 extension demo for 3DES-CBC mode. StudentID=23302010049.
```

回环结果为 True，说明拓展实现可以正确完成加解密。

## 7 实验总结

> 选填。可以记录调试过程中出现的问题及解决方法、对实验结果的分析、对实验的改进意见等。

<!-- TODO: 填写实验总结 -->

本实验将“算法实现”和“工具使用”结合起来完成：前半部分手工补全 DES 核心流程，后半部分使用 OpenSSL 完成 AES 文件解密。通过本次实验，我对分组密码中置换、轮函数、子密钥调度之间的关系有了更具体的理解。

调试过程中最需要关注的是位宽和顺序问题，尤其在 S 盒和轮密钥阶段，任何细小偏差都会导致最终结果完全错误。加入标准测试向量后，能更高效地定位实现问题。拓展部分实现 3DES-CBC 后，也进一步验证了现有 DES 组件的可复用性。

---

## 自查清单

提交前请逐项确认，完成的项目在「完成情况」列填写 **done**。

| # | 检查项 | 完成情况 |
| --- | -------- | ---------- |
| 1 | `permutation_by_table` 函数已补全 | done |
| 2 | `generate_round_keys` 函数已补全 | done |
| 3 | `round_function` 函数已补全 | done |
| 4 | `encrypt` 函数已补全 | done |
| 5 | 源代码可运行（`python des_scaffold.py`） | done |
| 6 | DES 密文解密完成（第 4.5 节） | done |
| 7 | AES 密文解密完成（第 4.6 节） | done |
| 8 | 实验步骤与思路阐述清晰（第 4 节） | done |
| 9 | 实验结果已记录（第 5 节） | done |
| 10 | 扩展实验（选做加分，第 6 节） | done |
| 11 | 提交文件结构正确（见实验指导书） | done |
