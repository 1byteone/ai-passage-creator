package com.example.aipassagecreator.dataviz;

/** 字段画像：语义角色 + 是否数值 */
public record FieldProfile(String name, String semantic, boolean numeric) {
    public static final String TIME = "time";
    public static final String CATEGORY = "category";
    public static final String MEASURE = "measure";
}
