import urllib.request
import ssl
import os

def getLetterFreqs(text):
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
    if isinstance(text, bytes):
        text = text.decode('utf-8', errors='ignore')
    
    modifiedText = str(text.strip())
    modifiedText = modifiedText.replace(" ", "")
    modifiedText = " ".join(modifiedText.split())
    modifiedText = modifiedText.lower()
    return modifiedText

def download_progress(block_num, block_size, total_size):
    downloaded = block_num * block_size
    
    if total_size > 0:
        percent = min(downloaded * 100 / total_size, 100)
        bar_length = 40
        filled_length = int(bar_length * percent / 100)
        bar = '█' * filled_length + '-' * (bar_length - filled_length)
        downloaded_mb = downloaded / (1024 * 1024)
        total_mb = total_size / (1024 * 1024)
        print(f'\r下载进度: |{bar}| {percent:.1f}% ({downloaded_mb:.2f}/{total_mb:.2f} MB)', end='', flush=True)
        if downloaded >= total_size:
            print()
    else:
        downloaded_kb = downloaded / 1024
        print(f'\r已下载: {downloaded_kb:.2f} KB', end='', flush=True)

def getEncryptedData():
    encryptedFilePath = "https://raw.githubusercontent.com/noidentity29/AppliedCryptoPython/master/encryptedmoby.txt"
    local_file = "cipherText.txt"
    
    # 检查本地文件是否已存在
    if os.path.exists(local_file):
        print(f"✓ 本地文件已存在: {os.path.abspath(local_file)}")
        print(f"  文件大小: {os.path.getsize(local_file)} 字节")
        print(f"  修改时间: {os.path.getmtime(local_file)}")
        print("-" * 60)
        print(f"跳过下载，直接读取本地文件...")
    else:
        print(f"本地文件不存在，开始下载...")
        print(f"目标路径: {os.path.abspath(local_file)}")
        print(f"URL: {encryptedFilePath}")
        print("-" * 60)
        
        ssl_context = ssl._create_unverified_context()
        opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=ssl_context)
        )
        urllib.request.install_opener(opener)
        
        try:
            urllib.request.urlretrieve(encryptedFilePath, local_file, download_progress)
            print(f"\n✓ 文件下载完成，保存为: {local_file}")
        except Exception as e:
            print(f"\n✗ 下载失败: {e}")
            raise
        
        print("-" * 60)
    
    # 从本地文件读取内容
    print(f"正在从 {local_file} 读取内容...")
    with open(local_file, 'r', encoding='utf-8', errors='ignore') as f:
        readText = f.read()
    
    textOnly = getTextOnly(readText)
    print(f"✓ 文本处理完成，有效字符数: {len(textOnly)}")
    print()
    
    return textOnly

# 主程序
if __name__ == "__main__":
    cipherText = getEncryptedData()
    freqScore = getLetterFreqs(cipherText)
    
    print("=" * 60)
    print(f"The frequency score for this file is: {freqScore:.6f}")
    print("=" * 60)