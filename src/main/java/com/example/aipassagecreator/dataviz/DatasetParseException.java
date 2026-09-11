package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;

/**
 * 数据集解析失败（格式非法/超限）。消息面向用户，中文。
 * 继承 BusinessException 而非 RuntimeException：GlobalExceptionHandler 对裸 RuntimeException
 * 一律吞成 50000「系统错误」，中文校验消息对用户不可见；走业务异常分支才能原样返回。
 */
public class DatasetParseException extends BusinessException {
    public DatasetParseException(String message) {
        super(ErrorCode.PARAMS_ERROR, message);
    }
}
