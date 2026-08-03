package com.example.aipassagecreator.handwriting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 手写渲染配置。
 * 由 application.yml 的 handwriting.render 段注入。
 */
@Configuration
@ConfigurationProperties(prefix = "handwriting.render")
public class HandwritingRenderConfig {

    /** COS 基础域名（Playwright 白名单路由放行） */
    private String cosBaseUrl = "";

    /** 单页渲染超时秒数，默认 30 */
    private int renderTimeoutSec = 30;

    /** 批次渲染超时秒数，默认 120 */
    private int batchTimeoutSec = 120;

    /** 渲染视口宽度（A4 比例），默认 1240 */
    private int viewportWidth = 1240;

    /** 渲染视口高度（A4 比例），默认 1754 */
    private int viewportHeight = 1754;

    /** 渲染设备缩放比，默认 2.0 */
    private double deviceScaleFactor = 2.0;

    public String getCosBaseUrl() { return cosBaseUrl; }
    public void setCosBaseUrl(String cosBaseUrl) { this.cosBaseUrl = cosBaseUrl; }
    public int getRenderTimeoutSec() { return renderTimeoutSec; }
    public void setRenderTimeoutSec(int renderTimeoutSec) { this.renderTimeoutSec = renderTimeoutSec; }
    public int getBatchTimeoutSec() { return batchTimeoutSec; }
    public void setBatchTimeoutSec(int batchTimeoutSec) { this.batchTimeoutSec = batchTimeoutSec; }
    public int getViewportWidth() { return viewportWidth; }
    public void setViewportWidth(int viewportWidth) { this.viewportWidth = viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public void setViewportHeight(int viewportHeight) { this.viewportHeight = viewportHeight; }
    public double getDeviceScaleFactor() { return deviceScaleFactor; }
    public void setDeviceScaleFactor(double deviceScaleFactor) { this.deviceScaleFactor = deviceScaleFactor; }
}
