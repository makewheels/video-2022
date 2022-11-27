package com.github.makewheels.video2022;

import cn.hutool.core.io.FileUtil;
import com.github.makewheels.video2022.etc.password.RSAUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class RSATests {
    @Test
    public void generateKeyPairs() {
        Map<String, String> map = RSAUtil.generateKeyPairs();
        String publicKey = map.get("publicKey");
        String privateKey = map.get("privateKey");
        System.out.println("publicKey = " + publicKey);
        System.out.println("privateKey = " + privateKey);
    }

    @Test
    public void encrypt() {
        String plain = "";
        System.out.println("plainText = " + plain);

        String publicKey = FileUtil.readUtf8String("D:\\workSpace\\~keys\\video-2022\\publicKey.txt");

        String cipher = RSAUtil.encrypt(publicKey, plain);
        System.out.println("cipher = " + cipher);

    }

    @Test
    public void decrypt() {
        String cipher = "";
        System.out.println("cipher = " + cipher);

        String privateKey = FileUtil.readUtf8String("D:\\workSpace\\~keys\\video-2022\\privateKey.txt");

        String plain = RSAUtil.decrypt(privateKey, cipher);
        System.out.println("plain = " + plain);

    }
}
