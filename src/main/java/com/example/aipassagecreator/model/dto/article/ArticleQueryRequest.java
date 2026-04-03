package com.example.aipassagecreator.model.dto.article;

import com.example.aipassagecreator.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询文章请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ArticleQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态
     */
    private String status;

    private static final long serialVersionUID = 1L;
}
