import urllib.request
import ssl
import os
from itertools import cycle
from functools import reduce

# ============ 配置常量 ============
ENCRYPTED_URL = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/encryptedmoby.txt"
DICTIONARY_URL = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/common_en_words.txt"
ENCRYPTED_FILE = "encryptedmoby.txt"
DICTIONARY_FILE = "common_en_words.txt"
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


def download_progress(block_num, block_size, total_size):
    """下载进度回调函数"""
    downloaded = block_num * block_size
    
    if total_size > 0:
        percent = min(downloaded * 100 / total_size, 100)
        bar_length = 40
        filled_length = int(bar_length * percent / 100)
        bar = '█' * filled_length + '-' * (bar_length - filled_length)
        downloaded_kb = downloaded / 1024
        total_kb = total_size / 1024
        print(f'\r下载进度: |{bar}| {percent:.1f}% ({downloaded_kb:.1f}/{total_kb:.1f} KB)', end='', flush=True)
        if downloaded >= total_size:
            print()
    else:
        downloaded_kb = downloaded / 1024
        print(f'\r已下载: {downloaded_kb:.1f} KB', end='', flush=True)


def download_file(url, local_file):
    """通用下载函数（带本地缓存检查）"""
    if os.path.exists(local_file):
        file_size = os.path.getsize(local_file)
        print(f"✓ 本地文件已存在: {local_file} ({file_size} 字节)")
        return True
    
    print(f"正在下载: {url}")
    print(f"保存到: {os.path.abspath(local_file)}")
    
    ssl_context = ssl._create_unverified_context()
    opener = urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=ssl_context)
    )
    urllib.request.install_opener(opener)
    
    try:
        urllib.request.urlretrieve(url, local_file, download_progress)
        print(f"✓ 下载完成")
        return True
    except Exception as e:
        print(f"\n✗ 下载失败: {e}")
        return False


def load_file(local_file, mode='r'):
    """从本地文件加载内容"""
    try:
        with open(local_file, mode, encoding='utf-8', errors='ignore') as f:
            return f.read()
    except Exception as e:
        print(f"✗ 读取文件失败 {local_file}: {e}")
        return None


# ============ 数据获取函数 ============

def getEncryptedData():
    """获取加密文本（带本地缓存）"""
    if not download_file(ENCRYPTED_URL, ENCRYPTED_FILE):
        raise Exception("无法获取加密文件")
    
    print(f"正在读取加密文件...")
    readText = load_file(ENCRYPTED_FILE)
    textOnly = getTextOnly(readText)
    print(f"✓ 加密文本处理完成，长度: {len(textOnly)} 字符")
    return textOnly


def getDictionary():
    """获取常用词典（带本地缓存）"""
    if not download_file(DICTIONARY_URL, DICTIONARY_FILE):
        raise Exception("无法获取词典文件")
    
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
    """使用重合指数法猜测密钥长度"""
    highest = 0
    highCtr = 0
    encryptedText = encryptedText.lower()
    
    print("正在分析密钥长度...")
    for key_len in range(1, 26):
        # 按密钥长度采样：取每第key_len个字符
        sampling = encryptedText[::key_len]
        freqCheck = getLetterFreqs(sampling)
        
        if highest < freqCheck:
            highest = freqCheck
            highCtr = key_len
    
    print(f"✓ 最可能的密钥长度: {highCtr} (频率分数: {highest:.4f})")
    return highCtr


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


def findKeyPos(message, keyLength, keyPos):
    """找出密钥第keyPos位置可能的字母（0-25）"""
    frequency = {}
    allKeys = []
    tolerance = 0.01
    
    # 提取该位置的所有字符（每隔keyLength个取一个，从keyPos开始）
    lowerMessage = message.lower()
    sampling = lowerMessage[keyPos::keyLength]
    
    if len(sampling) == 0:
        return [0]
    
    # 计算采样段的字母频率
    for ascii_code in range(ord('a'), ord('a') + 26):
        char = chr(ascii_code)
        frequency[char] = float(sampling.count(char)) / len(sampling)
    
    # 尝试每个可能的位移（1-25），看哪个能让频率分布最接近英语
    for possible_key in range(1, 26):
        sum_f_sqr = 0.0
        for ltr in NORMAL_FREQS:
            # 将标准频率表的字母位移possible_key位
            caesar_guess = shiftBy(ltr, possible_key)
            freqCalc = NORMAL_FREQS[ltr] * frequency[caesar_guess]
            sum_f_sqr += freqCalc
        
        # 如果接近英语的自然频率(0.065)，则可能是正确答案
        engValue = abs(sum_f_sqr - 0.065)
        if engValue < tolerance:
            allKeys.append(possible_key)
    
    # 如果没有找到，默认返回0（A）
    if len(allKeys) == 0:
        allKeys.append(0)
    
    return allKeys


def getKey(encrypted, key_length, dictionary):
    """破解完整密钥"""
    keys = []
    
    print(f"正在破解密钥（长度={key_length}）...")
    print("这可能需要几分钟，请耐心等待...")
    
    for pos in range(0, key_length):
        print(f"  分析密钥第 {pos+1}/{key_length} 位...")
        keyPos = findKeyPos(encrypted, key_length, pos)
        answerLen = len(keyPos)
        answerIndex = 0
        
        # 如果该位置有多个候选，用适应度分数选择最佳
        if answerLen > 1:
            defaultKey = keys[:]
            testKey = keys[:]
            defaultKey.append(keyPos[0])
            
            decrypted = decryptIndex(defaultKey, encrypted)
            defaultScore = getFitnessScore(decrypted, dictionary)
            
            for a in range(1, answerLen):
                testKey = keys[:]  # 重置testKey
                testKey.append(keyPos[a])
                decrypted = decryptIndex(testKey, encrypted)
                testScore = getFitnessScore(decrypted, dictionary)
                
                if testScore > defaultScore:
                    answerIndex = a
                    defaultScore = testScore
        
        keys.append(keyPos[answerIndex])
    
    # 构建最终密钥字符串
    fullKey = ""
    for i in range(len(keys)):
        fullKey = fullKey + chr(keys[i] + 65)
    
    print(f"✓ 密钥破解完成: {fullKey}")
    return fullKey


# ============ 主程序 ============

def main():
    print("=" * 60)
    print("维吉尼亚密码自动破解工具")
    print("=" * 60)
    print()
    
    # 加载数据
    myDictionary = getDictionary()
    print()
    cipherText = getEncryptedData()
    print()
    
    # 分析阶段
    print("-" * 60)
    freqScore = getLetterFreqs(cipherText)
    fitScore = getFitnessScore(cipherText, myDictionary)
    keyLength = getKeyLength(cipherText)
    print("-" * 60)
    print()
    
    # 破解密钥（这可能需要较长时间）
    decryptKey = getKey(cipherText, keyLength, myDictionary)
    print()
    
    # 输出结果
    print("=" * 60)
    print("破解结果:")
    print(f"  字母频率分数:  {freqScore:.20f}")
    print(f"  文本适应度:    {fitScore:.2f}")
    print(f"  推测密钥长度:  {keyLength}")
    print(f"  破解的密钥:    {decryptKey}")
    print("=" * 60)
    
    # 尝试解密并显示前100个字符
    print()
    print("使用破解的密钥解密前100个字符:")
    keys = [ord(c) - 65 for c in decryptKey]
    decrypted = decryptIndex(keys, cipherText[:10000])
    print(decrypted)


if __name__ == "__main__":
    main()