package com.github.makewheels.video2022;

import cn.hutool.core.io.FileUtil;
import com.github.makewheels.video2022.utils.RSAUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class RSATests {
    @Test
    public void generateKeyPairs() {
        Map<String, String> map = RSAUtil.generateKeyPairs();
        String privateKey = map.get("privateKey");
        String publicKey = map.get("publicKey");
        System.out.println("privateKey = " + privateKey);
        System.out.println("publicKey = " + publicKey);
    }

    @Test
    public void encrypt() {
        String plain = "cJJx3LZgpiZkIrIC";
        System.out.println("plainText = " + plain);

        String publicKey = FileUtil.readUtf8String("D:\\workSpace\\~keys\\video-2022\\publicKey.txt");

        String cipher = RSAUtil.encrypt(publicKey, plain);
        System.out.println("cipher = " + cipher);

    }

    @Test
    public void decrypt() {
        String cipher = "jXtCCfW53qlgax3P5CVwpwA/ENi/ZNL4+BJfgwRAfxt/xyc6z52E09JRYil6+GB0SozQmU68wFlwlVx6OGipbK0eq6BVZe9KsJoSH58VBsGRdyiH2jW2+HGDZXS+er3WcYTOuqQKybuTABxh2DBf9O/DTvbNuGNQdxMVdewleXI=";
        System.out.println("cipher = " + cipher);

        String privateKey = FileUtil.readUtf8String("D:\\workSpace\\~keys\\video-2022\\privateKey.txt");

        String plain = RSAUtil.decrypt(privateKey, cipher);
        System.out.println("plain = " + plain);

    }
}
