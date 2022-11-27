package com.github.makewheels.video2022.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.setting.dialect.Props;
import com.github.makewheels.video2022.Video2022Application;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PasswordUtil {
    /**
     * 获取application同级目录
     */
    private static String getFolderPath() {
        return Video2022Application.class.getResource("/").getFile();
    }

    /**
     * 获取解密私钥
     */
    private static String getPrivateKey() {
        return FileUtil.readUtf8String("C:\\Users\\thedoflin\\Downloads\\privateKey.txt");
    }

    /**
     * 解密替换文件
     *
     * @param configFileName   要被替换的配置文件
     * @param passwordFileName 密文
     */
    private static void handleSingleFile(String configFileName, String passwordFileName) {
        File configFile = new File(getFolderPath(), configFileName);
        File passwordFile = new File(getFolderPath(), passwordFileName);
        Props passwords = new Props(passwordFile);
        Set<Object> keySet = passwords.keySet();
        for (Object key : keySet) {
            String value = passwords.getStr((String) key);
            System.out.println(key + " " + value);
        }
    }

    public static void load() {
        //配置文件和，密文文件，映射关系
        Map<String, String> map = new HashMap<>();
        map.put("application.properties", "passwords-dev.properties");
        map.put("application-prod.properties", "passwords-prod.properties");

        //遍历处理每一个文件
        Set<String> keySet = map.keySet();
        for (String key : keySet) {
            handleSingleFile(key, map.get(key));
        }
    }
}
