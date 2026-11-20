package com.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.entity.RepairOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工单 Mapper。
 */
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /**
     * 按主键查询并加行锁（FOR UPDATE），用于状态流转入口，
     * 防止并发重复操作（如两个管理员同时派单）。
     * 原生 SQL 不经过 MP 逻辑删除插件，需手动带 deleted=0。
     */
    @Select("SELECT * FROM repair_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RepairOrder selectByIdForUpdate(@Param("id") Long id);

    /**
     * 看板统计：各状态工单数（一条 SQL 聚合，避免逐状态查库）。
     * 返回每行两个字段：status、cnt。
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM repair_order WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectStatusCount();

    /**
     * 看板统计：近 7 天每日新增数（created_at）与完成数。
     * 一条 SQL 同时产出两列：day、new_count、done_count。
     * 完成数口径与顶部完成率一致：COMPLETED / CONFIRMED 按 finish_time 统计，
     * REJECTED 按 updated_at 统计（REJECTED 为终态且无 finish_time，updated_at 即驳回时刻）。
     *
     * @param start 统计起点（今天往前 6 天的 00:00:00，共 7 天）
     */
    @Select("SELECT day, SUM(new_cnt) AS new_count, SUM(done_cnt) AS done_count FROM ("
            + "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, 1 AS new_cnt, 0 AS done_cnt "
            + "FROM repair_order WHERE deleted = 0 AND created_at >= #{start} "
            + "UNION ALL "
            + "SELECT DATE_FORMAT(finish_time, '%Y-%m-%d') AS day, 0 AS new_cnt, 1 AS done_cnt "
            + "FROM repair_order WHERE deleted = 0 AND finish_time IS NOT NULL AND finish_time >= #{start} "
            + "UNION ALL "
            + "SELECT DATE_FORMAT(updated_at, '%Y-%m-%d') AS day, 0 AS new_cnt, 1 AS done_cnt "
            + "FROM repair_order WHERE deleted = 0 AND status = 'REJECTED' "
            + "AND updated_at IS NOT NULL AND updated_at >= #{start}"
            + ") t GROUP BY day")
    List<Map<String, Object>> selectTrend(@Param("start") LocalDateTime start);
}
