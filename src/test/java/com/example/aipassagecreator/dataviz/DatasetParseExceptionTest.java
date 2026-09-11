package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatasetParseExceptionTest {

    @Test
    void isBusinessException_soChineseMessageReachesUser() {
        // GlobalExceptionHandler 对裸 RuntimeException 返回 50000「系统错误」，
        // 中文校验消息会丢失；必须走业务异常分支才可见
        DatasetParseException ex = new DatasetParseException("列数超过 50 上限，请精简后再试");
        assertInstanceOf(BusinessException.class, ex);
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertEquals("列数超过 50 上限，请精简后再试", ex.getMessage());
    }
}
