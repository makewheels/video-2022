package com.github.makewheels.video2022.etc.password;

import cn.hutool.core.io.FileUtil;
import com.github.makewheels.video2022.Video2022Application;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
public class PasswordUtil {

    /**
     * 获取解密私钥
     */
    private static String getPrivateKey(String env) {
        String path = null;
        if (env.equals("dev")) {
            path = "D:/workSpace/~keys/video-2022/privateKey.txt";
        } else if (env.equals("prod")) {
            path = "/root/keys/video-2022/privateKey.txt";
        }
        return FileUtil.readUtf8String(path);
    }

    /**
     * 获取密码文件
     */
    private static InputStream getPasswordFileInputStream(String env) {
        String path = null;
        if (env.equals("dev")) {
            path = "passwords-dev.properties";
        } else if (env.equals("prod")) {
            path = "passwords-prod.properties";
        }
        return Video2022Application.class.getResourceAsStream("/" + path);
    }

    /**
     * 获得解密之后的map
     */
    public static Map<String, String> getPlainTextMap(String env) {
        //加载解密私钥
        String privateKey = getPrivateKey(env);
        //加载密码文件
        Properties properties = new Properties();
        try {
            properties.load(getPasswordFileInputStream(env));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //遍历密码文件
        Map<String, String> map = new HashMap<>(properties.size());
        Set<String> keySet = properties.stringPropertyNames();
        for (String key : keySet) {
            String cipher = properties.getProperty(key);
            //解密
            String plain = RSAUtil.decrypt(privateKey, cipher);
            //放入map
            map.put(key, plain);
        }
        return map;
    }

}