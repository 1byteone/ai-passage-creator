package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ComicEpisodeMapper;
import com.example.aipassagecreator.mapper.ComicMonthlyVolumeMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import com.example.aipassagecreator.model.po.ComicEpisodePo;
import com.example.aipassagecreator.model.po.ComicMonthlyVolumePo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 漫画手帐浏览端点（本人可见） */
@RestController
@RequestMapping("/comic")
@RequiredArgsConstructor
public class ComicController {

    private final ComicBookService bookService;
    private final ComicEpisodeMapper episodeMapper;
    private final ComicMonthlyVolumeMapper volumeMapper;
    private final UserService userService;

    @GetMapping("/books")
    @Operation(summary = "我的手帐档案列表")
    public BaseResponse<List<ComicBookPo>> listBooks(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(bookService.listBooks(user.getId()));
    }

    @GetMapping("/books/{bookId}/months")
    @Operation(summary = "档案的月册列表")
    public BaseResponse<List<ComicMonthlyVolumePo>> listMonths(@PathVariable Long bookId,
                                                               HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        // 归属校验走档案本身（bookId → book.userId），与 getEpisode 同源，防跨用户越权浏览
        ComicBookPo book = bookService.getBookById(bookId);
        if (book == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "档案不存在");
        }
        if (!book.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该档案");
        }
        return ResultUtils.success(volumeMapper.selectListByQuery(
                QueryWrapper.create().eq("book_id", bookId).eq("is_delete", 0)
                        .orderBy("year_month", false)));
    }

    @GetMapping("/books/{bookId}/months/{yearMonth}/episodes")
    @Operation(summary = "月册的章节列表")
    public BaseResponse<List<ComicEpisodePo>> listEpisodes(@PathVariable Long bookId,
                                                           @PathVariable String yearMonth,
                                                           HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (yearMonth == null || !yearMonth.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数格式错误：年月份格式应为 YYYY-MM");
        }
        // 归属校验走档案本身（bookId → book.userId），与 getEpisode 同源，防跨用户越权浏览
        ComicBookPo book = bookService.getBookById(bookId);
        if (book == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "档案不存在");
        }
        if (!book.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该档案");
        }
        return ResultUtils.success(episodeMapper.selectListByQuery(
                QueryWrapper.create().eq("book_id", bookId).eq("year_month", yearMonth)
                        .eq("is_delete", 0).orderBy("episode_no", false)));
    }

    @GetMapping("/episodes/{episodeId}")
    @Operation(summary = "章节详情（含 pageHtml/pngUrl）")
    public BaseResponse<ComicEpisodePo> getEpisode(@PathVariable Long episodeId,
                                                   HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ComicEpisodePo po = episodeMapper.selectOneById(episodeId);
        if (po == null || (po.getIsDelete() != null && po.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "章节不存在");
        }
        // 归属校验走档案本身（bookId → book.userId），不能用 getOrCreateBook 会误建档案
        ComicBookPo book = bookService.getBookById(po.getBookId());
        if (book == null || !book.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该章节");
        }
        return ResultUtils.success(po);
    }
}
