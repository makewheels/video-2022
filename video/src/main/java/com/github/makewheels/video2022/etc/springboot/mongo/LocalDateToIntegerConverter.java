package com.github.makewheels.video2022.etc.springboot.mongo;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDate;

@ReadingConverter
@WritingConverter
public class LocalDateToIntegerConverter implements Converter<LocalDate, Integer> {
    @Override
    public Integer convert(LocalDate source) {
        return Integer.parseInt(source.toString().replace("-", ""));
    }
}
