package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.service.CosService;
import com.example.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/** 通用文件上传（漫画手帐照片等）→ COS 返回 URL */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class ComicFileController {

    private static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CosService cosService;
    private final UserService userService;

    @PostMapping("/upload")
    @Operation(summary = "上传图片（漫画手帐照片），返回可访问 URL")
    public BaseResponse<String> upload(@RequestParam("file") MultipartFile file,
                                       HttpServletRequest servletRequest) {
        userService.getLoginUser(servletRequest); // 登录校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/png/webp 图片");
        }
        try {
            String url = cosService.uploadBytes(file.getBytes(), contentType, "comic/photos");
            return ResultUtils.success(url);
        } catch (IOException e) {
            log.error("照片上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败，请重试");
        }
    }
}
