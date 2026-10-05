package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.model.custom.WarnLevelResult;

/**
 * 届期预警分级线 Service接口（rule-eval 形状：规则求值，无增删改）。
 * <p>全司归层只此一套口径：请线（发问那一刻在位）→ 取数（同一把舍法取齐）→
 * 比数贴线（压线归紧）→ 回话（层级与线代号、起用日同出一处）。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSecreWarnLineService {

    /** 按主键查询规则 */
    TSecreWarnLine selectTSecreWarnLineById(Long id);

    /**
     * 唯一判级回话：以发问那一刻 at 在位的线作数（多条时交班次序在前、再比代号字面序），
     * 离届日数 daysToDue 先按全司同一把舍法取齐再贴线，日数正好压线朝紧的一头归。
     * 三条线一条也没贴着时返回 {@link WarnLevelResult#NONE}：回话「不提醒」，
     * 线代号与起用日两格一并留空。
     */
    WarnLevelResult resolveWarn(BigDecimal daysToDue, Date at);

    /**
     * at 时刻在位名单：起用日已到（含当日）、交班日未到（不含当日）、未撤未删、
     * 三日数齐且从小到大压得住的线，按交班次序降序、代号字面序降序排定。
     * 屏上名单与从头捋册凑的名单同出于这一回计算。
     */
    List<TSecreWarnLine> listAvailable(Date at);

    /** at 时刻唯一作数的那条线（裁决后的头一条）；请不出在位线时返回 null */
    TSecreWarnLine activeAt(Date at);

    /** 单条线在 at 时刻是否在位且抬得出来（口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻在位条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 验一条线的三格日数抬不抬得出来。齐全且 th1&lt;th2&lt;th3 返回 null；
     * 否则回话点名：空的是哪一格、哪一格没能盖过它前头那一个。
     */
    String thresholdDefect(BigDecimal th1, BigDecimal th2, BigDecimal th3);

    /**
     * 旧算法（按线代号取值），形状照旧、不添东西也不改名目：
     * 按 ruleCode 取 at 时刻在位的那一版本，走同一套取数比数口径判 1..3 档；
     * 无在位版本／参数缺失／够不上线一律返回 0。日后按名录、按季度查线另起新条，不动这一条。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 旧算法（在位头一条取值），形状照旧；档位口径与 {@link #resolveWarn} 同一套 */
    int evaluateTop(BigDecimal input, Date at);
}
