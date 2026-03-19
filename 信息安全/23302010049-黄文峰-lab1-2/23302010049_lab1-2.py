from itertools import cycle
import os

def key_vigenere(key):
    keyArray = []
    for i in range(0,len(key)):
        keyElement = ord(key[i]) - 65 #A的ascii值为65
        keyArray.append(keyElement)
    return keyArray

def shiftEnc(c, offset):#根据位移量，向后位移offset得到新字母
    return chr((ord(c) - ord('A') + offset) % 26 + ord('A'))

def enc_vigenere(plaintext, key):
    secret = ''.join(shiftEnc(p, k) for p, k in zip(plaintext, cycle(key)))
    return secret

def shiftDec(c, offset):#根据位移量，向前位移offset得到原字母
    return chr((ord(c) - ord('A') + 26 - offset) % 26 + ord('A')) #加26是为了避免负数取余，尽管这里如果c是大写字母的话不可能出现这种情况

def dec_vigenere(ciphertext, key):
    plain = ''.join(shiftDec(c, k) for c, k in zip(ciphertext, cycle(key)))
    return plain

def main():
    # 加密测试
    plaintext_test = "THEBASICOFCRYPTOGRAPHY"
    key_encrypt = "SECURITY"
    expected_ciphertext = "LLGVRABAGJELPXMMYVCJYG"
    encrypted = enc_vigenere(plaintext_test, key_vigenere(key_encrypt))
    print("加密测试结果:", encrypted)
    print("加密测试是否通过:", encrypted == expected_ciphertext)

    # 解密测试
    ciphertext_test = "YBHBNXCFOSHLBPGTAUACMS"
    key_decrypt = "FUDAN"
    expected_plaintext = "THEBASICOFCRYPTOGRAPHY"
    decrypted = dec_vigenere(ciphertext_test, key_vigenere(key_decrypt))
    print("解密测试结果:", decrypted)
    print("解密测试是否通过:", decrypted == expected_plaintext)

    # 文件解密：只处理字母字符，避免换行或空格影响计算
    input_path = os.path.join(os.path.dirname(__file__), "lab1-2_input.txt")
    output_path = os.path.join(os.path.dirname(__file__), "lab1-2_output.txt")
    file_key = key_vigenere("CRYPTOGRAPHY")

    with open(input_path, "r", encoding="utf-8") as file:
        ciphertext = file.read()

    ciphertext = ''.join(c for c in ciphertext.upper() if 'A' <= c <= 'Z')
    plaintext = dec_vigenere(ciphertext, file_key)

    with open(output_path, "w", encoding="utf-8") as file:
        file.write(plaintext)

    print("文件解密完成，结果已保存到:", output_path)

if __name__ == '__main__':
    main()