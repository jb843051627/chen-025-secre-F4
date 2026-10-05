package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.custom.StaleWarnStamp;
import com.fc.v2.model.custom.WarnLevelResult;

/**
 * 涉密载体册面 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITSecreCarrierService {

    /** 按主键查询 */
    TSecreCarrier selectTSecreCarrierById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper);

    /** 新增 */
    int insertTSecreCarrier(TSecreCarrier record);

    /** 修改 */
    int updateTSecreCarrier(TSecreCarrier record);

    /** 批量删除 */
    int deleteTSecreCarrierByIds(String ids);

    /** 按主键删除 */
    int deleteTSecreCarrierById(Long id);

    /**
     * 给一件载体挂届期提醒：以发问那一刻 at 在位的线归层，层级连同线代号、起用那一日
     * 一并钉进载体那一行；够不上三条线时三格一并清空（不提醒），不许留半拉钉子。
     * 返回的回话与落行的那一对必须完全合得拢。
     */
    WarnLevelResult applyWarn(Long carrierId, BigDecimal daysToDue, Date at);

    /**
     * 钉档对不拢挑单：屏上挂着线代号、按发问那一刻 at 翻回册里却请不出在位线的件，
     * 一单一单挑出来（查无此线／已撤下／不在时段／起用日合不拢／钉档残缺）。
     */
    List<StaleWarnStamp> listStaleWarnStamp(Date at);
}
