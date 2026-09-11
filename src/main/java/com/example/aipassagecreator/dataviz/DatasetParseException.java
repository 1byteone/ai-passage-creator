package com.example.aipassagecreator.dataviz;

/** 数据集解析失败（格式非法/超限）。消息面向用户，中文。 */
public class DatasetParseException extends RuntimeException {
    public DatasetParseException(String message) { super(message); }
}
