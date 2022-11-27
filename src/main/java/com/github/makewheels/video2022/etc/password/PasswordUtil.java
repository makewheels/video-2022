package com.github.makewheels.video2022.etc.password;

import cn.hutool.core.io.FileUtil;
import cn.hutool.setting.dialect.Props;
import com.github.makewheels.video2022.utils.RSAUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private static File getPasswordFile(String env) {
        String path = null;
        if (env.equals("dev")) {
            path = "passwords-dev.properties";
        } else if (env.equals("prod")) {
            path = "passwords-prod.properties";
        }
        try {
            return ResourceUtils.getFile("classpath:" + path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获得解密之后的map
     */
    public static Map<String, String> getPlainTextMap(String env) {
        //加载解密私钥
        String privateKey = getPrivateKey(env);
        //加载密码文件
        Props props = new Props(getPasswordFile(env), StandardCharsets.UTF_8);

        //遍历密码文件
        Map<String, String> map = new HashMap<>(props.size());
        Set<Object> keySet = props.keySet();
        for (Object key : keySet) {
            String cipher = props.getStr((String) key);
            //解密，放入map
            String plain = RSAUtil.decrypt(privateKey, cipher);
            map.put((String) key, plain);
        }
        return map;
    }

}