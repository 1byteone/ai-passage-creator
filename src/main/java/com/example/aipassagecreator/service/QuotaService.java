package com.example.aipassagecreator.service;


import com.example.aipassagecreator.model.po.User;

/**
 * 配额服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface QuotaService {

    /**
     * 检查用户是否有足够的配额
     *
     * @param user 用户
     * @return 是否有配额
     */
    boolean hasQuota(User user);

    /**
     * 消耗配额（扣减1次）
     *
     * @param user 用户
     */
    void consumeQuota(User user);

    /**
     * 检查并消耗配额（原子操作）
     * 如果配额不足会抛出异常
     *
     * @param user 用户
     */
    void checkAndConsumeQuota(User user);

    /**
     * 检查并消耗配额（原子操作），可自定义配额不足时的提示
     *
     * @param user                用户
     * @param insufficientMessage 配额不足时的错误提示
     */
    void checkAndConsumeQuota(User user, String insufficientMessage);

    /**
     * 退还配额（补偿操作）
     * <p>
     * 用于配额已扣减但用户未获得产出的场景：
     * <ul>
     *     <li>异步派发失败（任务从未执行）</li>
     *     <li>执行过程失败（如 LLM 调用异常）</li>
     * </ul>
     * admin/VIP 未扣减过配额，因此直接跳过。
     *
     * @param user 用户
     */
    void refundQuota(User user);
}
