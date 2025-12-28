package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Converter(autoApply = false)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return Optional.ofNullable(attribute)
                .map(formatter::format)
                .orElse(null);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        return Optional.ofNullable(dbData)
                .filter(s -> !s.isBlank())
                .map(s -> LocalDateTime.parse(s, formatter))
                .orElse(null);
    }
}
