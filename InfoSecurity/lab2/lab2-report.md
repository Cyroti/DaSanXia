# 《信息安全》实验报告

| 项目 | 内容 |
|------|------|
| 实验名称 | DES & AES |
| 姓名 | |
| 学号 | |
| 日期 | |

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
- 编程语言：Python
- 运行命令：`python des_scaffold.py`
- AES 解密工具：OpenSSL / PyCryptodome

## 4 实验步骤与思路

> 结合代码说明算法设计思想与实现步骤。

### 4.1 permutation_by_table 实现

<!-- TODO: 描述置换函数的设计思路与关键代码 -->

### 4.2 generate_round_keys 实现

<!-- TODO: 描述子密钥生成的设计思路与关键代码 -->

### 4.3 round_function 实现

<!-- TODO: 描述轮函数的设计思路与关键代码（扩展E、异或、S盒、置换P） -->

### 4.4 encrypt 实现

<!-- TODO: 描述加密/解密主函数的设计思路与关键代码（Feistel网络、IP/IP_INV） -->

### 4.5 DES 解密密文

<!-- TODO: 记录解密过程和结果 -->
<!-- 学号尾号奇数：解密 cipher_text_odd = 0x9b99d07d9980305e -->
<!-- 学号尾号偶数：解密 cipher_text_even = 0x6f612748df99a70c -->
<!-- key = FudanNiu -->

### 4.6 AES 解密密文

<!-- TODO: 记录使用 DES 解密结果作为 AES 密钥的解密过程和结果 -->
<!-- 学号尾号奇数：解密 odd.enc -->
<!-- 学号尾号偶数：解密 even.enc -->

## 5 实验结果及分析

> 通过复制或截图的方式记录实验执行的结果。

<!-- TODO: 粘贴 DES 解密结果 -->

<!-- TODO: 粘贴 AES 解密后的明文内容 -->

<!-- TODO: 简要分析 DES 算法的特点 -->

## 6 扩展实验（选做，加分项）

> 实现各种加密模式（mode）、3DES，可有适当加分。

<!-- TODO: 如完成扩展实验，在此描述思路、实现与结果 -->

## 7 实验总结

> 选填。可以记录调试过程中出现的问题及解决方法、对实验结果的分析、对实验的改进意见等。

<!-- TODO: 填写实验总结 -->

---

## 自查清单

提交前请逐项确认，完成的项目在「完成情况」列填写 **done**。

| # | 检查项 | 完成情况 |
|---|--------|----------|
| 1 | `permutation_by_table` 函数已补全 | |
| 2 | `generate_round_keys` 函数已补全 | |
| 3 | `round_function` 函数已补全 | |
| 4 | `encrypt` 函数已补全 | |
| 5 | 源代码可运行（`python des_scaffold.py`） | |
| 6 | DES 密文解密完成（第 4.5 节） | |
| 7 | AES 密文解密完成（第 4.6 节） | |
| 8 | 实验步骤与思路阐述清晰（第 4 节） | |
| 9 | 实验结果已记录（第 5 节） | |
| 10 | 扩展实验（选做加分，第 6 节） | |
| 11 | 提交文件结构正确（见实验指导书） | |
