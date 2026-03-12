# 《信息安全》实验报告

| 项目 | 内容 |
|------|------|
| 实验名称 | Playfair 密码破译 |
| 姓名 | 黄文峰 |
| 学号 | 23302010049 |
| 日期 | 2026/3/11 |

---

## 1 实验目的

- 了解古典密码中的加密和解密运算
- 了解古典密码体制
- 掌握古典密码的破译方法

## 2 实验原理

Playfair 密码是一种基于 5×5 字母矩阵的双字母替换密码。使用密钥构建矩阵后，将明文按两个字母一组进行加密，加密规则取决于字母对在矩阵中的位置关系（同行、同列或矩形）。

本实验使用**模拟退火算法**破译 Playfair 密码：从随机密钥出发，不断修改密钥并评估解密结果与真实英文的相似度（基于四字母组频率统计），在迭代中逐步逼近正确密钥。

Simulated Annealing 模拟退火算法的基本原理如下：

它是一种元启发式算法（和特定问题结合成为具体的启发式算法），也就是要进行局部优化，为了得到全局最优解，引入了跳出局部最优解的方法。
$$
假设Y(i)是第i步的解。\\
1. 若f(Y(i+1)) \le f(Y(i)), 说明优化了，接受第i+1步的移动。\\
2. 若f(Y(i+1)) > f(Y(i)), 虽然没有优化，但是提供了一个跳出可能的局部最优解的机会，我们以一定的概率来接受第i+1步移动。\\
这里的概率遵循Metroplis准则。\\
p=e^{-\frac{\Delta E}{kt}}, t代表温度。 实际上这就是高中学过的玻尔兹曼因子， k是玻尔兹曼常数。
$$
自然界中，如果对物体进行快速冷却(Quenching)，物体最后会处于非晶态，能量并不最低。如果对物体进行缓慢冷却，也就是退火(Annealing),物体最后会处于晶态，能量最低。我们这里的温度和跳出概率挂钩，代表系统逐渐趋于稳定，逐渐收敛。

在算法实现中，温度变化和步长有关系，有线性和指数等下降方式。需要的参数还有每个状态下产生多少新的待选择状态。

关于参数的选择， 如下相关介绍中提到可以进行sensitivity analysis, 多跑几轮，试试那种参数选择是最好的。

> Sensitivity analysis is a  reasonable method for finding appropriate values for the SA parameters.  Sensitivity analysis prescribes a combination of parameters with which  the SA algorithm is run for several times. Several other combinations  are chosen and the algorithm is run several times with each of them.  A comparison of the results calculated from many runs provides guidance  about a suitable choice of the SA parameters. (**Bozorg-Haddad, O., Solgi, M., & Loáiciga, H. A. (2017). Meta-heuristic and evolutionary algorithms for engineering optimization. John Wiley & Sons.**)

https://www.math.cmu.edu/~gautam/c/2024-387/notes/10-simulated-annealing.html

注意到我们的代码宏定义了SA算法的一些基本参数，后续可以自己试试不同参数是否有更好的效果。

```c++
#define TEMP 20
#define STEP 0.2
#define COUNT 10000
```


## 3 实验环境

- 操作系统：<!-- TODO: 填写你的操作系统 -->
- 编程语言：C
- 编译命令：`gcc -O3 -lm playfaircrack.c scoreText.c -o your_name`

## 4 实验内容

1. 补全 `playfaircrack.c` 中的 `playfairDecipher` 和 `playfairCrack` 两个函数
   - `playfairDecipher`：根据密钥解密密文，输出明文（密文中的 J 用 I 代替）
   - `playfairCrack`：模拟退火函数，输入密文和初始密钥，输出迭代过程中最好的结果
2. 编译运行 `playfaircrack.c`，观察并判断程序输出
3. 使用 `verify.py` 验证解密结果
4. 完成实验报告

## 5 实验思路

> 结合代码说明算法设计思想与实现步骤。

<!-- TODO: 说明你对 playfairDecipher 的实现思路 -->

### 5.1 playfairDecipher 实现

<!-- TODO: 描述解密函数的设计思路与关键代码 -->

按照课本的意思，我们是有一个$5\times5 $大小的秘钥矩阵(j暂且被认为和i是同一个字母，j不再出现)。为了索引，我写了一个简单的把二维坐标映射到一维索引的辅助函数.

注意到我们如果需要进行key square的逆映射，知道字母的二维坐标很重要，方便知道对应二元字母组。所以还需要一个辅助函数把索引转化为坐标。

同时因为这些信息最开始没有给出，我们要遍历key字符数组来得到这些信息，存起来。

```c
int indexKeySquare(int row, int col) {
    assert(row <= 4 && row >= 0 && col <= 4 && col >= 0);//防御性编程
    return row * WIDTH + col * WIDTH;
}
```

这和`scoreTextQgram.c`当中的把四元组坐标映射到一维索引是一样的。

```c
score += qgram[17576*temp[0] + 676*temp[1] + 26*temp[2] + temp[3]];
```

设len为密文长度，按照`playfair`的实现，加密得到的密文由于明文分组的时候会填充X长度必然为偶数，我们需要从头开始，把下标为$[i, i+1(0\le i\le len-2 \and i \%2 == 0)]$的两个字母视为一个单元进行解密（值得注意的是这里明确要求在明文和密文中都不存在字母j）。每个二元字母组会映射到新的二元字母组。我们得到长度仍然为len的初步解密文本`PrePlaintext`。由于明文当中相邻重复字母或者长度要求也会填充字母X, 我们需要再次进行处理，以便得到最终的明文。

这里X有两种可能，一个是填充位，一个是明文中含有的。假设初步解密文本`PrePlaintext`中含有n个`由X经过key矩阵变化来的字母`, 我们则需要依次验证这n个字母是否需要去掉的可能性，产生的潜在明文都需要对其正确性进行验证（注意，这并不是SA的新状态，SA当中的状态变化也就是新solution在这里定义为发现新的key），这个复杂度是指数的。对一个秘钥的结果验证都需要`O(2^n)`的时间复杂度， 难以接受。 于是我暂且假设原始明文不会出现X字母。

下面是我的流程图，更加好理解一点，初步解密文本`PrePlaintext`也就是次明文`PostPlaintext`

![](./assets/playfair-2026-03-12-1716.svg)

图中所提到的`key square加/解密`很大程度上就是一个对应法则，看二元组的字母的三种存在方式决定如何选择对应的二元组。这是Playfair加密算法的原理。

注意到`verify.py          验证解密明文，确保明文中包含分割重复字符添加的 'X'`

所以实际上在这个程序中我们得到PrePlaintext即可。不需要去除X字母。

但是由于i和j被视作了同一个字母，我们还需要考虑这个可能性。

### 5.2 playfairCrack 实现

<!-- TODO: 描述模拟退火函数的设计思路与关键代码 -->

## 6 实验结果

> 通过复制或截图的方式记录实验执行的结果。

<!-- TODO: 粘贴程序输出结果，或插入截图 -->

## 7 扩展实验（选做）

> 使用暴力穷举 / 双字母词频分析法破译 Playfair 密码，不使用模拟退火。

<!-- TODO: 如完成扩展实验，在此描述思路、实现与结果 -->

## 8 实验总结

> 选填。可以记录调试过程中出现的问题及解决方法、对实验结果的分析、对实验的改进意见等。

<!-- TODO: 填写实验总结 -->

---

## 自查清单

提交前请逐项确认，完成的项目在「完成情况」列填写 **done**。

| # | 检查项 | 完成情况 |
|---|--------|----------|
| 1 | `playfairDecipher` 函数已补全 | |
| 2 | `playfairCrack` 函数已补全 | |
| 3 | 源代码可编译运行（`gcc -O3 -lm playfaircrack.c scoreText.c -o your_name`） | |
| 4 | 程序运行结果正确（输出可辨认的英文明文） | |
| 5 | 使用 `verify.py` 验证通过 | |
| 6 | 实验思路阐述清晰（第 5 节） | |
| 7 | 实验结果已记录（第 6 节） | |
| 8 | 扩展实验（选做，第 7 节） | |
| 9 | 提交文件结构正确（见实验指导书） | |
