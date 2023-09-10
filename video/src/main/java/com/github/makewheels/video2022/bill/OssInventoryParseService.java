package com.github.makewheels.video2022.bill;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class OssInventoryParseService {
    /**
     * <a href="https://doc.hutool.cn/pages/CsvUtil">hutool csv</a>
     */
    public void parse() {
        CsvData data = CsvUtil.getReader().read(FileUtil.file(
                "C:\\Users\\thedoflin\\Downloads\\7b6f5016-7cd5-4d73-99d4-8257902cfc4a.csv"));
        for (CsvRow row : data.getRows()) {
            String key = URLDecoder.decode(row.get(1), StandardCharsets.UTF_8);
            System.out.println(key);
        }
    }

    public static void main(String[] args) {
        new OssInventoryParseService().parse();
    }
}
