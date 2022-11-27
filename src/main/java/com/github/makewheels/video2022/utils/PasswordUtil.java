package com.github.makewheels.video2022.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.setting.dialect.Props;
import com.github.makewheels.video2022.Video2022Application;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
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
    private static String getPrivateKey(File configFile) {
        //读取配置文件中的，私钥文件保存位置
        Props props = new Props(configFile, StandardCharsets.UTF_8);
        String filePath = props.getStr("password.privateKey.path");
        File file = new File(filePath);

        //因为有dev环境和prod环境，私钥可能不存在，那就直接跳过
        if (!file.exists()) return null;

        //读取私钥文件，返回
        return FileUtil.readUtf8String(file);
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

        log.info("configFile = " + configFile.getAbsolutePath());
        log.info("passwordFile = " + passwordFile.getAbsolutePath());

        //加载解密私钥
        String privateKey = getPrivateKey(configFile);
        //因为不同环境，私钥文件可能不存在，如果不存在就跳过
        if (privateKey == null) return;

        //源配置文件
        List<String> configLines = FileUtil.readUtf8Lines(configFile);

        //密码文件
        Props passwords = new Props(passwordFile, StandardCharsets.UTF_8);

        //遍历配置文件
        for (int i = 0; i < configLines.size(); i++) {
            String line = configLines.get(i);
            //跳过空行
            if (StringUtils.isEmpty(line)) continue;

            // aliyun.oss.accessKeyId
            String key = line.split("=")[0];
            //拿着对应的key到秘钥文件里找到密文
            //LTAI5tbkS0Ti3ayiUkfAoPZL
            String cipher = passwords.getStr(key);

            //有的配置有密码覆盖，有的没有，如果没有就跳过
            if (cipher == null) continue;
            //解密
            String plain = RSAUtil.decrypt(privateKey, cipher);
            //回写配置文件
            configLines.set(i, key + "=" + plain);
        }

        //覆盖源配置文件
        FileUtil.writeUtf8Lines(configLines, configFile);
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
