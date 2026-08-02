package com.fitai.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fitai.health.model.entity.UserHealthDO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface UserHealthMapper extends BaseMapper<UserHealthDO> {
    // 基础的单表增删改查已由 MyBatis-Plus 的 BaseMapper 提供，无需声明
}