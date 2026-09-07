package com.anssa8bu.ssafy.device.entity;

public enum DeviceType {
    UMI_CAMERA("UMI_Camera"),
    JETSON("JETSON");

    private final String databaseValue;

    DeviceType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static DeviceType fromDatabaseValue(String value) {
        for (DeviceType type : values()) {
            if (type.databaseValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown device type: " + value);
    }
}
