import urllib.request
import ssl
import os

# ============ 配置常量 ============
ENCRYPTED_URL = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/encryptedmoby.txt"
DICTIONARY_URL = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/common_en_words.txt"
ENCRYPTED_FILE = "encryptedmoby.txt"
DICTIONARY_FILE = "common_en_words.txt"
ALPHA = 'abcdefghijklmnopqrstuvwxyz'


# ============ 核心函数 ============

def getLetterFreqs(text):
    """计算字母频率的平方和（用于检测是否为自然语言）"""
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
    """清理文本：去除空格、转小写、保留纯字母"""
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
        print(f"✓ 本地文件已存在: {local_file} ({os.path.getsize(local_file)} 字节)")
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
        # 按密钥长度采样（取每第key_len个字符）
        # 这里正好用到了python的切片语法特性，切片步长为key_len
        sampling = encryptedText[::key_len]
        freqCheck = getLetterFreqs(sampling)
        
        if highest < freqCheck:
            highest = freqCheck
            highCtr = key_len
            print(f"  尝试长度 {key_len:2d}: 频率分数 = {freqCheck:.4f} [当前最佳]")
        else:
            print(f"  尝试长度 {key_len:2d}: 频率分数 = {freqCheck:.4f}")
    
    print(f"✓ 最可能的密钥长度: {highCtr} (频率分数: {highest:.4f})")
    return highCtr


# ============ 主程序 ============

def main():
    print("=" * 60)
    print("维吉尼亚密码频率分析工具")
    print("=" * 60)
    print()
    
    # 加载数据
    myDictionary = getDictionary()
    print()
    cipherText = getEncryptedData()
    print()
    
    # 分析
    print("-" * 60)
    freqScore = getLetterFreqs(cipherText)
    fitScore = getFitnessScore(cipherText, myDictionary)
    keyLength = getKeyLength(cipherText)
    print("-" * 60)
    
    # 输出结果
    print()
    print("=" * 60)
    print("分析结果:")
    print(f"  字母频率分数:  {freqScore:.6f}")
    print(f"  文本适应度:    {fitScore:.2f}")
    print(f"  推测密钥长度:  {keyLength}")
    print("=" * 60)


if __name__ == "__main__":
    main()