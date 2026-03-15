package com.github.makewheels.video2022.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 压缩工具类
 */
@Slf4j
public class CompressUtil {
    /**
     * 解压gz文件
     *
     * @param gzFile       gz压缩文件
     * @param targetFolder 目标文件夹
     */
    public static void unCompressGz(File gzFile, File targetFolder) {
        try {
            byte[] bytes = ZipUtil.unGzip(Files.newInputStream(gzFile.toPath()));
            String uncompressedFilename = GzipUtils.getUncompressedFilename(gzFile.getName());
            File uncompressFile = new File(targetFolder, uncompressedFilename);
            FileUtil.mkParentDirs(uncompressFile);
            FileUtil.writeBytes(bytes, uncompressFile);
        } catch (IOException e) {
            log.error("解压gz文件失败, 错误堆栈:" + ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }
}
