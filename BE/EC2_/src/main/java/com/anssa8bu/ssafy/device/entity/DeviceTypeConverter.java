package com.anssa8bu.ssafy.device.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DeviceTypeConverter implements AttributeConverter<DeviceType, String> {

    @Override
    public String convertToDatabaseColumn(DeviceType attribute) {
        return attribute == null ? null : attribute.getDatabaseValue();
    }

    @Override
    public DeviceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DeviceType.fromDatabaseValue(dbData);
    }
}
