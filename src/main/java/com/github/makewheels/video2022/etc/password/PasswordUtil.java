package com.github.makewheels.video2022.etc.password;

import cn.hutool.core.io.FileUtil;
import cn.hutool.setting.dialect.Props;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class PasswordUtil {

    /**
     * 获取解密私钥
     */
    private String getPrivateKey(File configFile) {
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
     * 获得解密之后的map
     */
    public Map<String, String> getPlainTextMap(String env) {
        //加载解密私钥
        String privateKey = getPrivateKey(new File(""));

        //密码文件
        Props passwords = new Props(new File(""), StandardCharsets.UTF_8);

        //遍历密码文件

//        for (int i = 0; i < configLines.size(); i++) {
//            String line = configLines.get(i);
//            //跳过空行
//            if (StringUtils.isEmpty(line)) continue;
//
//            // aliyun.oss.accessKeyId
//            String key = line.split("=")[0];
//            //拿着对应的key到秘钥文件里找到密文
//            //LTAI5tbkS0Ti3ayiUkfAoPZL
//            String cipher = passwords.getStr(key);
//
//            //有的配置有密码覆盖，有的没有，如果没有就跳过
//            if (cipher == null) continue;
//            //解密
//            String plain = RSAUtil.decrypt(privateKey, cipher);
//        }
        return null;

    }

}