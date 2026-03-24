#!/usr/bin/env python3
"""
SPN (Substitution-Permutation Network) 简洁实现
- 分组长度: 16 bit
- 密钥长度: 32 bit (4轮，每轮8bit子密钥)
- 轮数: 4
"""

# S盒: 4-bit 替换 (非线性)
S_BOX = [0xE, 0x4, 0xD, 0x1, 0x2, 0xF, 0xB, 0x8,
         0x3, 0xA, 0x6, 0xC, 0x5, 0x9, 0x0, 0x7]

# 逆S盒 (解密用)
INV_S_BOX = [S_BOX.index(i) for i in range(16)]

# P盒: 16-bit 置换 (位置i的比特移动到位置P_BOX[i])
P_BOX = [0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15]

def sbox_layer(x, sbox):
    """16-bit 分为4个nibble，分别过S盒"""
    return (sbox[x & 0xF] | #低四位， 第3到第0位。
            (sbox[(x >> 4) & 0xF] << 4) | #第7位到第4位
            (sbox[(x >> 8) & 0xF] << 8) |
            (sbox[(x >> 12) & 0xF] << 12))


def permute(x):
    """16-bit P盒置换"""
    result = 0
    for i in range(16):
        i_bit = (x >> i) & 1
        result |= i_bit << P_BOX[i] #相当于把第i比特（自低向高，从0开始）移动到第P_BOX[i]比特的位置上。
    return result


def key_schedule(key):
    """生成4个8-bit子密钥 (从32-bit主密钥中提取)"""
    return [(key >> (24 - i * 8)) & 0xFF for i in range(4)]


def add_key(x, k):
    """密钥加: 16-bit状态与8-bit子密钥异或 (子密钥扩展为16-bit)"""
    k16 = ((k & 0xF0) << 8) | ((k & 0x0F) << 4) | (k & 0xFF)
    return x ^ k16


def round_spn(x, k, sbox, last=False):
    """单轮SPN: 密钥加 -> S盒 -> (P盒，最后一轮省略)"""
    x = add_key(x, k)
    x = sbox_layer(x, sbox)
    if not last:
        x = permute(x)
    return x


def encrypt(plaintext, key):
    """SPN加密"""
    keys = key_schedule(key)
    x = plaintext
    for i in range(3):
        x = round_spn(x, keys[i], S_BOX)
    # 最后一轮: 无P盒，加最终密钥加
    x = add_key(x, keys[3])
    x = sbox_layer(x, S_BOX)
    x = add_key(x, keys[3])  # 白化密钥
    return x & 0xFFFF


def decrypt(ciphertext, key):
    """SPN解密 (逆序操作)"""
    keys = key_schedule(key)
    x = ciphertext
    # 逆最后一轮
    x = add_key(x, keys[3])
    x = sbox_layer(x, INV_S_BOX)
    x = add_key(x, keys[3])
    # 逆前3轮
    for i in range(2, -1, -1):
        x = permute(x)  # P盒是自逆的
        x = sbox_layer(x, INV_S_BOX)
        x = add_key(x, keys[i])
    return x & 0xFFFF


# ============ 测试 ============
if __name__ == "__main__":
    key = 0x12345678      # 32-bit 密钥
    pt = 0xABCD           # 16-bit 明文
    
    ct = encrypt(pt, key)
    dt = decrypt(ct, key)
    
    print(f"明文: 0x{pt:04X}")
    print(f"密文: 0x{ct:04X}")
    print(f"解密: 0x{dt:04X}")
    print(f"验证: {'✓ 成功' if dt == pt else '✗ 失败'}")