package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;

/**
 * 图片检索服务接口
 * 抽象图片检索逻辑，便于扩展多种图片来源（如 Pexels、Unsplash、AI 生图等）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ImageSearchService {

    /**
     * 根据请求获取图片（推荐使用此方法）
     *
     * @param request 图片请求对象，包含keywords.prompt等参数
     * @return 图片 URL，获取失败返回 null
     */
    default String getImage(ImageRequest  request){
        //默认实现：提取服务类型选择合适的参数
        String param = request.getEffectiveParam(getMethod().isAiGenerated());
        return searchImage(param);
    }

    default ImageData getImageData(ImageRequest request) {
        //默认实现：通过getImage获取URL,然后转换为ImageData
        String url = getImage(request);
        return ImageData.fromUrl(url);
    }

    /**
     * 根据关键词检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL，检索失败返回 null
     */
    String searchImage(String keywords);

    /**
     * 获取图片检索方式
     *
     * @return 图片检索方式枚举
     */
    ImageMethodEnum getMethod();

    /**
     * 获取降级图片 URL
     *
     * @param position 位置序号（用于生成唯一的随机图片）
     * @return 降级图片 URL
     */
    String getFallbackImage(int position);

    /**
     * 判断服务是否可用
     * 子类可以重写此方法进行健康检查
     *
     * @return 是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}
