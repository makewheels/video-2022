package com.github.makewheels.video2022.etc.springboot.mongo;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDate;

@ReadingConverter
@WritingConverter
public class IntegerToLocalDateConverter implements Converter<Integer, LocalDate> {
    @Override
    public LocalDate convert(Integer source) {
        return LocalDateTimeUtil.parse(source.toString(), "yyyyMMdd").toLocalDate();
    }
}
