package com.fitai.health.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * <p>问题背景：{@code UserHealthDO} 上的 createTime / updateTime 标注了
 * {@code @TableField(fill = FieldFill.INSERT)} 与 {@code INSERT_UPDATE}，
 * 但项目此前没有提供 MetaObjectHandler 实现，导致自动填充从未生效，
 * 插入记录时 create_time / update_time 实际落库为 null。</p>
 *
 * <p>本类补齐该能力：插入时填充创建时间与更新时间，更新时刷新更新时间。</p>
 *
 * @author 郑新跃
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
