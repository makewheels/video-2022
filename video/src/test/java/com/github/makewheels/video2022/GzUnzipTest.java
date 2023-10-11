package com.github.makewheels.video2022;

import cn.hutool.core.util.IdUtil;
import com.github.makewheels.video2022.utils.CompressUtil;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GzUnzipTest {
    @Test
    public void unzip() {
        File gzFile = new File("D:\\2345Downloads\\5a525f2b-4645-4752-b1ab-a840b5964c13.csv.gz");
        File targetFolder = new File(gzFile.getParentFile(), IdUtil.fastSimpleUUID());
        CompressUtil.unCompressGz(gzFile, targetFolder);
    }
}
