package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import com.example.aipassagecreator.model.vo.MethodologyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 创作方法论模板 API — 模板市场（前端创作页卡片式选择器数据源）
 */
@Slf4j
@RestController
@RequestMapping("/methodology")
@Tag(name = "MethodologyController", description = "创作方法论模板")
public class MethodologyController {

    @Resource
    private MethodologyRegistry methodologyRegistry;

    /**
     * 获取全部方法论模板（精简 VO，供模板选择器展示）
     */
    @GetMapping("/list")
    @Operation(summary = "获取方法论模板列表")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<List<MethodologyVO>> list() {
        List<MethodologyVO> vos = new ArrayList<>();
        for (String name : methodologyRegistry.getNames()) {
            MethodologyDefinition def = methodologyRegistry.get(name);
            MethodologyVO vo = MethodologyVO.builder()
                    .name(def.getName())
                    .description(def.getDescription())
                    .platformName(def.getPlatform() != null ? def.getPlatform().getName() : null)
                    .audience(def.getPlatform() != null ? def.getPlatform().getAudience() : null)
                    .minChars(def.getPlatform() != null ? def.getPlatform().getMinChars() : null)
                    .maxChars(def.getPlatform() != null ? def.getPlatform().getMaxChars() : null)
                    .cardStyle(def.getPlatform() != null ? def.getPlatform().getCardStyle() : null)
                    .dimensionNames(def.getCreationDimensions() != null
                            ? def.getCreationDimensions().stream().map(MethodologyDefinition.CreationDimension::getName).toList()
                            : List.of())
                    .build();
            vos.add(vo);
        }
        return ResultUtils.success(vos);
    }
}
