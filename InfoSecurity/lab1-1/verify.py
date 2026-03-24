import hashlib

hash_answer = '77eb452f0f9310186dc6d3e562ab400e'


# 用于验证待确认明文，请确保明文中包含分割重复字符的‘X’
def verify(plain_text: str):
    return hashlib.md5(plain_text.encode(encoding='UTF-8')).hexdigest() == hash_answer
