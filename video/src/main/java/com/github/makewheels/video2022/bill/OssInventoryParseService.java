package com.github.makewheels.video2022.bill;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

@Service
public class OssInventoryParseService {
    /**
     * <a href="https://doc.hutool.cn/pages/CsvUtil">hutool csv</a>
     */
    public void parse() {
        CsvData data = CsvUtil.getReader().read(FileUtil.file(
                "C:\\Users\\thedoflin\\Downloads\\7b6f5016-7cd5-4d73-99d4-8257902cfc4a.csv"));
        for (CsvRow row : data.getRows()) {
            System.out.println(JSON.toJSONString(row));
        }
    }

    public static void main(String[] args) {
        new OssInventoryParseService().parse();
    }
}
