package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.utils;

import java.io.File;

/**
 * 文件路径工具类
 */
public class FilepathUtils {
    /**
     * 获取项目根目录
     * D:\workSpace\intellijidea\video-2022
     */
    public static File getProjectDir() {
        return new File(System.getProperty("user.dir"));
    }

    /**
     * 获取src目录
     * D:\workSpace\intellijidea\video-2022\src
     */
    public static File getSrcDir() {
        return new File(getProjectDir(), "src");
    }

    /**
     * 获取test目录
     * D:\workSpace\intellijidea\video-2022\src\test
     */
    public static File getTestDir() {
        return new File(getSrcDir(), "test");
    }

    /**
     * 获取test/java目录
     * D:\workSpace\intellijidea\video-2022\src\test\java
     */
    public static File getTestJavaDir() {
        return new File(getTestDir(), "java");
    }

    /**
     * 获取ChromeDriver文件
     */
    public static File getChromeDriver() {
        return new File(getTestJavaDir(), "com/github/makewheels/video2022/chromedriver.exe");
    }
}
