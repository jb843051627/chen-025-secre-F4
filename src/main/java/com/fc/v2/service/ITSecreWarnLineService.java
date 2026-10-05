package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSecreWarnLine;

/**
 * 届期预警分级线 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * <p>归层口径全司只有这一处给说法，四问钉死，谁也不许替它另抓一把尺子：</p>
 * <ol>
 * <li><b>在位那一条线怎么回取</b>：未删、未撤、起用那一日（含）已经过去、交班那一日（不含）
 * 还没到；旧册手抄线交班那一格空着的，一律当一直在位。补判旧批发问的件，请的是那一拨件
 * 各自发问当下在位的线，不是此刻最新那一条。</li>
 * <li><b>三条线（三档）都够不上时给不给说法</b>：给——判档 4「不提醒」，回话那一栏老老实实落
 * 「不提醒」，线代号与起用那一日两格一并留着不填；不许照旧退回，更不许抄近旁那条线的数字
 * 拼一个看着体面的答复。</li>
 * <li><b>两条并序时听谁的</b>：交班次序（priority）数值大的先开口；次序并列比线代号，字面序
 * 排在后头的那条赢。同一时刻问出两个层级，这件事本身就是错。</li>
 * <li><b>日数正好压在分界上时往哪一头落</b>：朝紧的一头归（一级最紧）。取数与比数是两道工，
 * 先照同一把舍法（统一 2 位、四舍五入）把数取齐，取齐之后才许贴线。</li>
 * </ol>
 *
 * <p>一条线身上三个日数（起算/够线/封顶）空了一格、或排后面的压不过排前面的，这条线当下就
 * 抬不出来；哪一格不对由 {@link #checkLine} 逐格点明，不许只丢一句「这几个数不对」。
 * 钉进载体那一行的线代号与起用那一日，就认 {@link #findInPosition}／{@link #listAvailable}
 * 当下给出的那一对——挪旁边那条的数字、或者拿去年那一季的线来配，都算错。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSecreWarnLineService {

    /** 按主键翻册（旧年立的、中途撤下的也只许这样翻着看，不许替当下答话） */
    TSecreWarnLine selectTSecreWarnLineById(Long id);

    /**
     * 单规则求值（按线代号取值的旧算法，形状照旧，不添东西也不改名目）：
     * 按 ruleCode 回取 at 时刻在位的那一条判档（1..3；三档够不上 = 4「不提醒」）。
     * 当刻请不出在位线 / 该线当下抬不出来 / 参数缺失 一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 多规则求值：at 时刻在位名单里裁决出的头一条开口判档（1..3；三档够不上 = 4「不提醒」）。
     * 无在位线 / 该开口的那一条当下抬不出来 / 参数缺失 一律返回 0。
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * at 时刻在位名单（屏上那份），按并列裁决顺序排好（交班次序降序、线代号字面序后者居前）；
     * 与把线册从头捋一遍凑出来的名单来自同一回计算。
     */
    List<TSecreWarnLine> listAvailable(Date at);

    /** 单条线在 at 时刻是否在位（批量导入/报表等旁路复用，口径与回取/名单同一处） */
    boolean usable(Long id, Date at);

    /** at 时刻在位线条数（列表页角标 / 看板），与 {@link #listAvailable} 同一回计算 */
    int countAvailable(Date at);

    /**
     * 按线代号回取 at 时刻在位的那一条线——钉牢线代号与起用那一日就认这一处给出的那一对；
     * 当刻请不出在位线返回 null（屏上挂着线代号、翻回册里请不出在位线的件，就靠它一单一单挑出来）。
     */
    TSecreWarnLine findInPosition(String ruleCode, Date at);

    /**
     * 比数校验回话：三格日数空的是哪一格、又是哪一格没能盖过它前头那一个，逐格点明；
     * 返回空列表表示这条线当下抬得出来。
     */
    List<String> checkLine(TSecreWarnLine line);
}
