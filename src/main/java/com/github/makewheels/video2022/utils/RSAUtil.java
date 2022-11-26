package com.github.makewheels.video2022.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * RSA工具类
 * <a href="https://hutool.cn/docs/#/crypto/%E9%9D%9E%E5%AF%B9%E7%A7%B0%E5%8A%A0%E5%AF%86-AsymmetricCrypto">
 * hutool工具类封装
 * </a>
 */
public class RSAUtil {

    /**
     * 生成公私钥对
     */
    public static Map<String, String> generateKeyPairs() {
        RSA rsa = new RSA();
        Map<String, String> map = new HashMap<>();
        map.put("privateKey", rsa.getPrivateKeyBase64());
        map.put("publicKey", rsa.getPublicKeyBase64());
        return map;
    }

    /**
     * 加密
     *
     * @param publicKey 公钥
     * @param plain     明文
     */
    public static String encrypt(String publicKey, String plain) {
        RSA rsa = new RSA(null, publicKey);
        byte[] cipher = rsa.encrypt(plain.getBytes(StandardCharsets.UTF_8), KeyType.PublicKey);
        return Base64.encode(cipher);
    }

    /**
     * 解密
     *
     * @param privateKey 私钥
     * @param cipher     密文
     */
    public static String decrypt(String privateKey, String cipher) {
        RSA rsa = new RSA(privateKey, null);
        byte[] plain = rsa.decrypt(Base64.decode(cipher), KeyType.PrivateKey);
        return new String(plain, StandardCharsets.UTF_8);
    }

}
