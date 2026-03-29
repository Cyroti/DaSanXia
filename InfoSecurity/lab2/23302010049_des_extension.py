from des_scaffold import encrypt

BLOCK_SIZE = 8


def _des_encrypt_block(block: int, key: int) -> int:
    return int(encrypt(block, key, decrypt=False), 16)


def _des_decrypt_block(block: int, key: int) -> int:
    return int(encrypt(block, key, decrypt=True), 16)


def triple_des_ede_encrypt_block(block: int, key1: int, key2: int,
                                 key3: int) -> int:
    step1 = _des_encrypt_block(block, key1)
    step2 = _des_decrypt_block(step1, key2)
    step3 = _des_encrypt_block(step2, key3)
    return step3


def triple_des_ede_decrypt_block(block: int, key1: int, key2: int,
                                 key3: int) -> int:
    step1 = _des_decrypt_block(block, key3)
    step2 = _des_encrypt_block(step1, key2)
    step3 = _des_decrypt_block(step2, key1)
    return step3


def pkcs7_pad(data: bytes, block_size: int = BLOCK_SIZE) -> bytes:
    pad_len = block_size - (len(data) % block_size)
    if pad_len == 0:
        pad_len = block_size
    return data + bytes([pad_len]) * pad_len


def pkcs7_unpad(data: bytes, block_size: int = BLOCK_SIZE) -> bytes:
    if not data or len(data) % block_size != 0:
        raise ValueError("Invalid padded data length")
    pad_len = data[-1]
    if pad_len < 1 or pad_len > block_size:
        raise ValueError("Invalid padding length")
    if data[-pad_len:] != bytes([pad_len]) * pad_len:
        raise ValueError("Invalid PKCS7 padding bytes")
    return data[:-pad_len]


def _split_blocks(data: bytes, block_size: int = BLOCK_SIZE) -> list[bytes]:
    return [data[i:i + block_size] for i in range(0, len(data), block_size)]


def triple_des_cbc_encrypt(data: bytes, key1: int, key2: int, key3: int,
                           iv: int) -> bytes:
    padded = pkcs7_pad(data)
    blocks = _split_blocks(padded)

    prev = iv
    encrypted_blocks: list[bytes] = []

    for block in blocks:
        block_int = int.from_bytes(block, byteorder="big")
        mixed = block_int ^ prev
        cipher_int = triple_des_ede_encrypt_block(mixed, key1, key2, key3)
        encrypted_blocks.append(
            cipher_int.to_bytes(BLOCK_SIZE, byteorder="big"))
        prev = cipher_int

    return b"".join(encrypted_blocks)


def triple_des_cbc_decrypt(cipher: bytes, key1: int, key2: int, key3: int,
                           iv: int) -> bytes:
    if len(cipher) % BLOCK_SIZE != 0:
        raise ValueError("Cipher length must be multiple of 8 bytes")

    blocks = _split_blocks(cipher)
    prev = iv
    plain_blocks: list[bytes] = []

    for block in blocks:
        block_int = int.from_bytes(block, byteorder="big")
        mixed = triple_des_ede_decrypt_block(block_int, key1, key2, key3)
        plain_int = mixed ^ prev
        plain_blocks.append(plain_int.to_bytes(BLOCK_SIZE, byteorder="big"))
        prev = block_int

    return pkcs7_unpad(b"".join(plain_blocks))


def run_extension_demo() -> None:
    key1 = int.from_bytes(b"FudanNiu", byteorder="big")
    key2 = int.from_bytes(b"WuhanV5!", byteorder="big")
    key3 = int.from_bytes(b"Crypto49", byteorder="big")
    iv = 0x1234567890ABCDEF

    plaintext = ("This is lab2 extension demo for 3DES-CBC mode. "
                 "StudentID=23302010049.").encode("utf-8")

    cipher = triple_des_cbc_encrypt(plaintext, key1, key2, key3, iv)
    recovered = triple_des_cbc_decrypt(cipher, key1, key2, key3, iv)

    print("[3DES-CBC Extension Demo]")
    print("plaintext bytes:", len(plaintext))
    print("cipher bytes   :", len(cipher))
    print("cipher hex head:", cipher.hex()[:64])
    print("roundtrip ok   :", recovered == plaintext)
    print("recovered text :", recovered.decode("utf-8"))


if __name__ == "__main__":
    run_extension_demo()
