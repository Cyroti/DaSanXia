import os
from itertools import cycle
from functools import reduce

# ============ 配置常量 ============
DICTIONARY_URL = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/common_en_words.txt"
DICTIONARY_FILE = "common_en_words.txt"
INPUT_FILE = "lab1-2_input.txt"           # 输入密文文件
OUTPUT_FILE = "lab1-2_friedman_output.txt" # 输出明文文件

ALPHA_LOWER = 'abcdefghijklmnopqrstuvwxyz'
ALPHA_UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

# 英语字母标准频率表
NORMAL_FREQS = {
    'a': 0.080642499002080981, 'b': 0.015373768624831691, 'c': 0.026892340312538593,
    'd': 0.043286671390026357, 'e': 0.12886234260657689, 'f': 0.024484713711692099,
    'g': 0.019625534749730816, 'h': 0.060987267963718068, 'i': 0.06905550211598431,
    'j': 0.0011176940633901926, 'k': 0.0062521823678781188, 'l': 0.041016761327711163,
    'm': 0.025009719347800208, 'n': 0.069849754102356679, 'o': 0.073783151266212627,
    'p': 0.017031440203182008, 'q': 0.0010648594165322703, 'r': 0.06156572691936394,
    's': 0.063817324270355996, 't': 0.090246649949305979, 'u': 0.027856851020401599,
    'v': 0.010257964235274787, 'w': 0.021192261444145363, 'x': 0.0016941732664605912,
    'y': 0.01806326249861108, 'z': 0.0009695838238376564
}

KAPPA_P = 0.0655
KAPPA_R = 1 / 26


# ============ 核心工具函数 ============

def shiftBy(c, n):
    """将字符c位移n位（凯撒加密）"""
    return chr(((ord(c) - ord('a') + n) % 26) + ord('a'))


def getLetterFreqs(text):
    """计算字母频率的平方和（重合指数）"""
    frequency = {}
    text_length = len(text)
    if text_length == 0:
        return 0.0
    
    for ascii_code in range(ord('a'), ord('a') + 26):
        char = chr(ascii_code)
        frequency[char] = float(text.count(char)) / text_length
    
    sum_freqs_squared = 0.0
    for ltr in frequency:
        sum_freqs_squared += frequency[ltr] * frequency[ltr]
    return sum_freqs_squared


def getIC(text):
    """按定义计算重合指数 IC = sum(ni*(ni-1)) / (N*(N-1))"""
    text = text.lower()
    N = len(text)
    if N < 2:
        return 0.0

    counts = {}
    for ch in ALPHA_LOWER:
        counts[ch] = text.count(ch)

    numerator = 0
    for ch in ALPHA_LOWER:
        numerator += counts[ch] * (counts[ch] - 1)

    return numerator / (N * (N - 1))


def getAverageGroupIC(text, key_len):
    """将密文按密钥长度分组后，计算各组 IC 的平均值"""
    if key_len <= 0:
        return 0.0

    ics = []
    for pos in range(key_len):
        segment = text[pos::key_len]
        if len(segment) >= 2:
            ics.append(getIC(segment))

    if not ics:
        return 0.0
    return sum(ics) / len(ics)


def estimateFriedmanKeyLength(text):
    """使用 Friedman 公式估计密钥长度"""
    N = len(text)
    if N < 2:
        return 1.0, 0.0

    ic = getIC(text)
    denominator = ((N - 1) * ic) - (KAPPA_R * N) + KAPPA_P
    if denominator <= 0:
        return 1.0, ic

    L = (0.027 * N) / denominator
    if L < 1:
        L = 1.0
    return L, ic


def getTextOnly(text):
    """清理文本：去除空格和非字母字符，转小写"""
    if isinstance(text, bytes):
        text = text.decode('utf-8', errors='ignore')
    
    modifiedText = str(text.strip())
    modifiedText = modifiedText.replace(" ", "")
    modifiedText = " ".join(modifiedText.split())
    modifiedText = modifiedText.lower()
    # 只保留字母
    modifiedText = ''.join(filter(str.isalpha, modifiedText))
    return modifiedText


def load_file(local_file, mode='r'):
    """从本地文件加载内容"""
    try:
        with open(local_file, mode, encoding='utf-8', errors='ignore') as f:
            return f.read()
    except Exception as e:
        print(f"✗ 读取文件失败 {local_file}: {e}")
        return None


def save_file(local_file, content):
    """保存内容到本地文件"""
    try:
        with open(local_file, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✓ 结果已保存到: {os.path.abspath(local_file)}")
        return True
    except Exception as e:
        print(f"✗ 保存文件失败 {local_file}: {e}")
        return False


# ============ 数据获取函数 ============

def getEncryptedData():
    """从本地文件读取加密文本"""
    print(f"正在读取本地密文文件: {INPUT_FILE}")
    
    if not os.path.exists(INPUT_FILE):
        raise Exception(f"找不到输入文件: {INPUT_FILE}")
    
    readText = load_file(INPUT_FILE)
    if readText is None:
        raise Exception("无法读取密文文件")
    
    textOnly = getTextOnly(readText)
    print(f"✓ 密文读取完成，有效字符数: {len(textOnly)}")
    return textOnly


def getDictionary():
    """获取常用词典（带本地缓存，保留联网下载）"""
    import urllib.request
    import ssl
    
    if os.path.exists(DICTIONARY_FILE):
        file_size = os.path.getsize(DICTIONARY_FILE)
        print(f"✓ 本地词典已存在: {DICTIONARY_FILE} ({file_size} 字节)")
    else:
        print(f"正在下载词典...")
        print(f"URL: {DICTIONARY_URL}")
        
        ssl_context = ssl._create_unverified_context()
        opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=ssl_context)
        )
        urllib.request.install_opener(opener)
        
        try:
            urllib.request.urlretrieve(DICTIONARY_URL, DICTIONARY_FILE)
            print(f"✓ 词典下载完成")
        except Exception as e:
            raise Exception(f"词典下载失败: {e}")
    
    print(f"正在读取词典...")
    fileOfWords = load_file(DICTIONARY_FILE)
    words = fileOfWords.split()
    # 过滤短词（长度>2）
    longerwords = list(filter(lambda x: len(x) > 2, words))
    print(f"✓ 词典加载完成，共 {len(longerwords)} 个长词")
    return longerwords


# ============ 密码分析核心 ============

def getFitnessScore(message, longerwords):
    """计算文本适应度分数（基于常用词匹配）"""
    score = 0.0
    message = message.lower()
    for word in longerwords:
        wordWeight = message.count(word)
        if wordWeight > 0:
            score += wordWeight * 50 * len(word)
    return score


def getKeyLength(encryptedText):
    """使用 Friedman 估计 + 分组 IC 细化猜测密钥长度"""
    encryptedText = encryptedText.lower()

    print("正在分析密钥长度...")
    estimated_len, full_ic = estimateFriedmanKeyLength(encryptedText)
    estimated_round = int(round(estimated_len))
    if estimated_round < 1:
        estimated_round = 1

    best_len = 1
    best_group_ic = 0.0
    best_score = float("inf")

    for key_len in range(1, 26):
        group_ic = getAverageGroupIC(encryptedText, key_len)
        # 让分组 IC 接近英文 κp，同时偏向 Friedman 估计值，避免选到其倍数。
        score = abs(group_ic - KAPPA_P) + 0.015 * abs(key_len - estimated_len)
        if score < best_score:
            best_score = score
            best_len = key_len
            best_group_ic = group_ic

    print(f"  全文 IC: {full_ic:.6f}")
    print(f"  Friedman 估计长度: {estimated_len:.2f}")
    print(f"✓ 最可能的密钥长度: {best_len} (分组IC: {best_group_ic:.4f})")
    return best_len


def decryptIndex(keys, ciphertext):
    """使用密钥列表解密维吉尼亚密码"""
    key = ""
    ciphertext = ciphertext.upper()
    
    # 构建密钥字符串
    for i in range(len(keys)):
        key = key + chr(keys[i] + 65)
    
    # 配对密文和密钥（密钥循环使用）
    pairs = list(zip(ciphertext, cycle(key)))
    result = ''
    
    for pair in pairs:
        # 计算密文字母减去密钥字母的位置
        total = reduce(lambda x, y: ALPHA_UPPER.index(x) - ALPHA_UPPER.index(y), pair)
        result += ALPHA_UPPER[total % 26]
    
    return result


def getCaesarChiSquareScore(segment, shift):
    """给定一个 Caesar 位移，计算解密后文本与英文频率的卡方分数（越小越好）"""
    if len(segment) == 0:
        return float("inf")

    decrypted = []
    for ch in segment:
        idx = (ord(ch) - ord('a') - shift) % 26
        decrypted.append(ALPHA_LOWER[idx])

    decrypted_text = ''.join(decrypted)
    N = len(decrypted_text)
    chi = 0.0

    for ltr in ALPHA_LOWER:
        observed = decrypted_text.count(ltr)
        expected = NORMAL_FREQS[ltr] * N
        if expected > 0:
            chi += ((observed - expected) ** 2) / expected

    return chi


def findKeyPos(message, keyLength, keyPos):
    """找出密钥第keyPos位置最可能的字母（0-25）"""
    
    # 提取该位置的所有字符（每隔keyLength个取一个，从keyPos开始）
    lowerMessage = message.lower()
    sampling = lowerMessage[keyPos::keyLength]
    
    if len(sampling) == 0:
        return 0

    best_shift = 0
    best_score = float("inf")
    for possible_key in range(0, 26):
        score = getCaesarChiSquareScore(sampling, possible_key)
        if score < best_score:
            best_score = score
            best_shift = possible_key

    return best_shift


def compressRepeatingKey(key):
    """如果密钥由更短子串重复组成，则压缩为最短周期"""
    key_len = len(key)
    for unit_len in range(1, key_len + 1):
        if key_len % unit_len != 0:
            continue
        unit = key[:unit_len]
        if unit * (key_len // unit_len) == key:
            return unit
    return key


def getKey(encrypted, key_length, dictionary, verbose=True):
    """破解完整密钥"""
    keys = []

    if verbose:
        print(f"正在破解密钥（长度={key_length}）...")
        print("这可能需要几分钟，请耐心等待...")
    
    for pos in range(0, key_length):
        if verbose:
            print(f"  分析密钥第 {pos+1}/{key_length} 位...")
        keyPos = findKeyPos(encrypted, key_length, pos)
        keys.append(keyPos)
    
    # 构建最终密钥字符串
    fullKey = ""
    for i in range(len(keys)):
        fullKey = fullKey + chr(keys[i] + 65)

    fullKey = compressRepeatingKey(fullKey)

    if verbose:
        print(f"✓ 密钥破解完成: {fullKey}")
    return fullKey


def refineKeyLength(encryptedText, dictionary, initial_len):
    """基于解密可读性对 Friedman 初始长度进行复核"""
    best_len = initial_len
    best_score = float("-inf")

    for key_len in range(1, 26):
        key_guess = getKey(encryptedText, key_len, dictionary, verbose=False)
        key_indexes = [ord(c) - 65 for c in key_guess]
        decrypted = decryptIndex(key_indexes, encryptedText)
        fitness = getFitnessScore(decrypted, dictionary)
        plain_ic = getIC(decrypted.lower())

        # 综合得分：优先可读词匹配，再参考明文 IC，并轻微惩罚过长密钥。
        score = fitness + (3000 * plain_ic) - (5 * key_len)

        if score > best_score:
            best_score = score
            best_len = key_len

    return best_len, best_score


# ============ 主程序 ============

def main():
    print("=" * 60)
    print("维吉尼亚密码自动破解工具 (Friedman分析)")
    print("=" * 60)
    print()
    
    # 检查输入文件是否存在
    if not os.path.exists(INPUT_FILE):
        print(f"✗ 错误: 找不到输入文件 '{INPUT_FILE}'")
        print(f"  请确保文件在当前目录: {os.getcwd()}")
        return
    
    # 加载数据
    try:
        myDictionary = getDictionary()
        print()
        cipherText = getEncryptedData()
        print()
    except Exception as e:
        print(f"✗ 数据加载失败: {e}")
        return
    
    # 分析阶段
    print("-" * 60)
    freqScore = getLetterFreqs(cipherText)
    fitScore = getFitnessScore(cipherText, myDictionary)
    keyLength = getKeyLength(cipherText)
    refinedKeyLength, refineScore = refineKeyLength(cipherText, myDictionary, keyLength)
    if refinedKeyLength != keyLength:
        print(f"  Friedman 结果复核后，采用密钥长度: {refinedKeyLength} (复核分数: {refineScore:.2f})")
        keyLength = refinedKeyLength
    else:
        print(f"  Friedman 结果复核通过，密钥长度保持: {keyLength} (复核分数: {refineScore:.2f})")
    print("-" * 60)
    print()
    
    # 破解密钥（这可能需要较长时间）
    decryptKey = getKey(cipherText, keyLength, myDictionary)
    print()
    
    # 解密完整文本
    print("正在解密完整文本...")
    keys = [ord(c) - 65 for c in decryptKey]
    decrypted = decryptIndex(keys, cipherText)
    
    # 保存结果到文件
    save_file(OUTPUT_FILE, decrypted)
    print()
    
    # 输出结果
    print("=" * 60)
    print("破解结果:")
    print(f"  输入文件:      {INPUT_FILE}")
    print(f"  输出文件:      {OUTPUT_FILE}")
    print(f"  字母频率分数:  {freqScore:.20f}")
    print(f"  文本适应度:    {fitScore:.2f}")
    print(f"  推测密钥长度:  {keyLength}")
    print(f"  破解的密钥:    {decryptKey}")
    print("=" * 60)
    
    # 显示解密文本
    print()
    print("解密文本:")
    preview = decrypted[::]
    print(preview)
    print(f"... (共 {len(decrypted)} 字符)")


if __name__ == "__main__":
    main()