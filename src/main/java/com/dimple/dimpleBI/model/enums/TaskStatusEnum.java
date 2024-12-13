package com.dimple.dimpleBI.model.enums;

public enum TaskStatusEnum {
    WAIT(1, "wait"),       // 等待
    RUNNING(2, "running"), // 执行中
    SUCCEED(3, "succeed"), // 成功
    FAILED(4, "failed");   // 失败

    private final int code;  // 状态码
    private final String description; // 状态描述

    // 构造器
    TaskStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    // 获取状态码
    public int getCode() {
        return code;
    }

    // 获取状态描述
    public String getDescription() {
        return description;
    }

    // 根据状态码获取对应的枚举
    public static TaskStatusEnum fromCode(int code) {
        for (TaskStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    // 根据状态描述获取对应的枚举
    public static TaskStatusEnum fromDescription(String description) {
        for (TaskStatusEnum status : values()) {
            if (status.getDescription().equalsIgnoreCase(description)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown description: " + description);
    }

    @Override
    public String toString() {
        return this.description;
    }
}
