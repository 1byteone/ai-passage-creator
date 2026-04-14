package com.example.aipassagecreator.agent.tools;


import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import com.example.aipassagecreator.service.CosService;
import com.example.aipassagecreator.service.ImageServiceStrategy;
import com.example.aipassagecreator.utils.GsonUtils;
import com.qcloud.cos.model.ciModel.metaInsight.ImageResult;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 图片生成工具
 * 封装 ImageServiceStrategy,供 Agent 调用
 */
@Component
@Slf4j
public class ImageGenerationTool {

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private CosService cosService;

    /**
     * 根据需求生成或搜索图片
     */
    @Tool(description = "根据需求生成或搜索图片,支持多种图片来源")
    public String generateImage(
        @ToolParam(description = "图片来源,可以是图片链接,图片base64,图片文件路径") String imageSource,
        @ToolParam(description = "图片关键词,用于搜索图片") String keywords ,
        @ToolParam(description = "AI 生图提示词或图表代码") String prompt ,
        @ToolParam(description = "图片位置序号") Integer position,
        @ToolParam(description = "图片类型：cover 或 section") String type,
        @ToolParam(description = "对应的章节标题") String sectionTitle
    ){

        log.info("ImageGenerationTool 开始执行: imageResource={},position={}", imageSource, position);

        try{
            ImageRequest request = ImageRequest.builder()
                .keywords(keywords)
                .prompt(prompt)
                .position(position)
                .type(type)
                .build();

            //使用统一上传到 cos 的方法
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, request);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            ImageGenerationResult generationResult = new ImageGenerationResult();
            generationResult.setPosition(position);
            generationResult.setUrl(cosUrl);
            generationResult.setMethod(method.getValue());
            generationResult.setKeywords(keywords);
            generationResult.setSectionTitle(sectionTitle);
            generationResult.setDescription(type);
            generationResult.setSuccess(true);

            log.info("ImageGenerationTool 执行成功: imageResource={}，position={}",imageSource, position );

            return GsonUtils.toJson(generationResult);
        }catch (Exception e){
            log.error("ImageGenerationTool 执行失败: imageResource={}，position={}",imageSource, position, e);

            ImageGenerationResult failResult=new ImageGenerationResult();
            failResult.setPosition(position);
            failResult.setSuccess(false);
            failResult.setError(e.getMessage());
            failResult.setSectionTitle(sectionTitle);

            return GsonUtils.toJson(failResult);
        }
    }

    /**
     * 直接生成图片(不通过Agent ,供内部调用)
     */
    public ImageGenerationResult generateImageDirect(String imageSource, String keywords, String prompt,
                                                     Integer position, String type, String sectionTitle,String placeholderId){
        try{
            ImageRequest imageRequest=ImageRequest.builder()
                    .keywords(keywords)
                    .prompt(prompt)
                    .position(position)
                    .type(type)
                    .build();

            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            ImageGenerationResult generationResult = new ImageGenerationResult();
            generationResult.setPosition(position);
            generationResult.setUrl(cosUrl);
            generationResult.setMethod(method.getValue());
            generationResult.setKeywords(keywords);
            generationResult.setSectionTitle(sectionTitle);
            generationResult.setDescription(type);
            generationResult.setPlaceholderId(placeholderId);
            generationResult.setSuccess(true);

            return generationResult;

        }catch (Exception e){
            log.error("图片生成失败：imageSource={},position={}",imageSource, position,e);

            ImageGenerationResult failResult=new ImageGenerationResult();
            failResult.setPosition(position);
            failResult.setSuccess(false);
            failResult.setError(e.getMessage());
            failResult.setSectionTitle(sectionTitle);
            failResult.setPlaceholderId(placeholderId);

            return failResult;

        }
    }

    /**
     * 图片生成结果
     */
    @Data
    public static class ImageGenerationResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer position;
        private String url;
        private String method;
        private String keywords;
        private String sectionTitle;
        private String description;
        private String placeholderId;
        private boolean success;
        private String error;

    }

}
