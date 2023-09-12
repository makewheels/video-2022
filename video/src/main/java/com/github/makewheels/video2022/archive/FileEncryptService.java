package com.github.makewheels.video2022.archive;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import org.springframework.stereotype.Service;

/**
 * 文件加解密服务
 */
@Service
public class FileEncryptService {
    public static void main(String[] args) {
        String content = "test中文";

//随机生成密钥
        byte[] key = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue(), 256).getEncoded();

//构建
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, key);

//加密
        byte[] encrypt = aes.encrypt(content);
//解密
        byte[] decrypt = aes.decrypt(encrypt);

//加密为16进制表示
        String encryptHex = aes.encryptHex(content);
//解密为字符串
        String decryptStr = aes.decryptStr(encryptHex, CharsetUtil.CHARSET_UTF_8);

        System.out.println(encryptHex);
        System.out.println(decryptStr);

    }

}
