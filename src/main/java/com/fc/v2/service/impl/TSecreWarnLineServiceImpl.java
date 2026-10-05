package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.model.custom.WarnLevelResult;
import com.fc.v2.service.ITSecreWarnLineService;

/**
 * 届期预警分级线 Service业务层处理（rule-eval 形状）。
 * <p>全司归层只此一套口径，屏上摆出第二套就算错：
 * <ol>
 *   <li>请线：只认发问那一刻在位的线——起用日已到（含）、交班日未到（不含）、未撤未删；
 *       旧年立的、新立的、撤下的都在册里躺着，只有这一条能替当下答话。</li>
 *   <li>取数与比数是两道工：离届日数与三格阈值先照同一把舍法（3 位、四舍五入）取齐，
 *       取齐之后才贴线，不许取的时候进一位、比的时候不进。</li>
 *   <li>并列：交班次序（priority）大的先开口；次序并列比线代号，字面序排在后头的赢。</li>
 *   <li>压线：日数正好踩在分界上朝紧的一头归（&lt;= 取下一档）。</li>
 *   <li>够不上三条线：回话「不提醒」，线代号与起用日两格一并留空，不许抄近旁那条凑数。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreWarnLineServiceImpl implements ITSecreWarnLineService {

    /** 同一把舍法：阈值列是 decimal(12,3)，取数也取到 3 位，四舍五入（HALF_UP） */
    private static final int DAY_SCALE = 3;

    /** 线的情形：0 在位 */
    private static final int STATUS_ACTIVE = 0;

    /**
     * 并列裁决：交班次序降序（数值大的先说话）；次序并列时按 ruleCode 字面序降序
     * （照字面序排在后头的那条赢）。null priority 按 0、null 代号按空串，排序确定。
     */
    private static final Comparator<TSecreWarnLine> PRIORITY_THEN_CODE_DESC = new Comparator<TSecreWarnLine>() {
        @Override
        public int compare(TSecreWarnLine a, TSecreWarnLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TSecreWarnLineMapper secreWarnLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在“起用当日 / 交班当日”这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TSecreWarnLine selectTSecreWarnLineById(Long id) {
        return this.secreWarnLineMapper.selectById(id);
    }

    @Override
    public WarnLevelResult resolveWarn(BigDecimal daysToDue, Date at) {
        if (daysToDue == null || at == null) {
            return WarnLevelResult.NONE;
        }
        TSecreWarnLine rule = activeAt(at);
        if (rule == null) {
            // 屏上挂着线代号、此刻却请不出在位线：不抄近旁那条拼答复，老老实实不提醒。
            return WarnLevelResult.NONE;
        }
        int level = levelOf(rule, daysToDue);
        if (level == 0) {
            return WarnLevelResult.NONE;
        }
        return WarnLevelResult.of(rule, level);
    }

    @Override
    public List<TSecreWarnLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TSecreWarnLine>();
        }
        List<TSecreWarnLine> all =
                this.secreWarnLineMapper.selectList(new QueryWrapper<TSecreWarnLine>().eq("del_flag", 0));
        List<TSecreWarnLine> active = new ArrayList<TSecreWarnLine>();
        for (TSecreWarnLine r : all) {
            if (inPosition(r, at)) {
                active.add(r);
            }
        }
        Collections.sort(active, PRIORITY_THEN_CODE_DESC);
        return active;
    }

    @Override
    public TSecreWarnLine activeAt(Date at) {
        List<TSecreWarnLine> active = listAvailable(at);
        return active.isEmpty() ? null : active.get(0);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TSecreWarnLine r = this.secreWarnLineMapper.selectById(id);
        return r != null && inPosition(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    @Override
    public String thresholdDefect(BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        StringBuilder defects = new StringBuilder();
        // 先点明空的是哪一格
        if (th1 == null) {
            appendDefect(defects, "起算日数这一格空着");
        }
        if (th2 == null) {
            appendDefect(defects, "够线日数这一格空着");
        }
        if (th3 == null) {
            appendDefect(defects, "封顶日数这一格空着");
        }
        if (th1 != null && th2 != null && th2.compareTo(th1) <= 0) {
            appendDefect(defects, "够线日数(" + th2.toPlainString() + ")没能盖过它前头的起算日数("
                    + th1.toPlainString() + ")");
        }
        if (th2 != null && th3 != null && th3.compareTo(th2) <= 0) {
            appendDefect(defects, "封顶日数(" + th3.toPlainString() + ")没能盖过它前头的够线日数("
                    + th2.toPlainString() + ")");
        }
        return defects.length() == 0 ? null : defects.toString();
    }

    private static void appendDefect(StringBuilder sb, String msg) {
        if (sb.length() > 0) {
            sb.append("；");
        }
        sb.append(msg);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        // 旧算法形状照旧：按代号取的也必须是发问当下在位的那一版，不许拿最新一条判旧件。
        for (TSecreWarnLine r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return levelOf(r, input);
            }
        }
        return 0;
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        TSecreWarnLine rule = activeAt(at);
        return rule == null ? 0 : levelOf(rule, input);
    }

    /**
     * 一条线在 at 时刻抬不抬得出来，全司只过这一道筛子（名单、定位、usable、旧算法共用）：
     * 未删、未撤、三格日数齐且从小到大压得住、起用日已到（含）、交班日未到（不含）。
     * 旧册手抄线交班格空着（eff_end 为空）的，当作一直在位——这道兼容口子保留。
     */
    private boolean inPosition(TSecreWarnLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        // 撤过的那一条：册里查得着、现行那排里也不许数上它，不配用来定层
        if (r.getStatus() == null || r.getStatus() != STATUS_ACTIVE) {
            return false;
        }
        if (thresholdDefect(r.getTh1Max(), r.getTh2Max(), r.getTh3Max()) != null) {
            return false;
        }
        String now = ts(at);
        if (r.getEffStart() == null || ts(r.getEffStart()).compareTo(now) > 0) {
            return false;
        }
        // 交班那一日(不含)：该日之前归旧线管，该日起往后归新线管
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(now) <= 0) {
            return false;
        }
        return true;
    }

    /**
     * 贴线：取数与比数同一把舍法——输入先取齐到 3 位（HALF_UP），阈值也按同一把取齐，
     * 取齐之后才比。压线朝紧的一头归：等于起算归一级、等于够线归二级、等于封顶归三级。
     * 够不上封顶返回 0（调用方落「不提醒」）。
     */
    private int levelOf(TSecreWarnLine rule, BigDecimal rawInput) {
        BigDecimal input = rawInput.setScale(DAY_SCALE, RoundingMode.HALF_UP);
        BigDecimal th1 = rule.getTh1Max().setScale(DAY_SCALE, RoundingMode.HALF_UP);
        BigDecimal th2 = rule.getTh2Max().setScale(DAY_SCALE, RoundingMode.HALF_UP);
        BigDecimal th3 = rule.getTh3Max().setScale(DAY_SCALE, RoundingMode.HALF_UP);
        if (input.compareTo(th1) <= 0) {
            return 1;
        }
        if (input.compareTo(th2) <= 0) {
            return 2;
        }
        if (input.compareTo(th3) <= 0) {
            return 3;
        }
        return 0;
    }
}
