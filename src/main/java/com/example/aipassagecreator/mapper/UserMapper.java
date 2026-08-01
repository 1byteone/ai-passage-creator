package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.User;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子扣减用户配额
     * 使用 quota > 0 条件确保并发安全，避免超扣
     *
     * @param userId 用户ID
     * @return 影响行数，1表示成功，0表示配额不足
     */
    @Update("UPDATE `user` SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
    int decrementQuota(@Param("userId") Long userId);

    /**
     * 原子退还用户配额
     * 用于任务派发失败等未真正消耗算力的场景，避免用户配额白扣
     *
     * @param userId 用户ID
     * @return 影响行数，1表示成功
     */
    @Update("UPDATE `user` SET quota = quota + 1 WHERE id = #{userId}")
    int incrementQuota(@Param("userId") Long userId);
}
