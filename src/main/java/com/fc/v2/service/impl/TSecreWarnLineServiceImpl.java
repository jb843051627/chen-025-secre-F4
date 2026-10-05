package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.service.ITSecreWarnLineService;

/**
 * 届期预警分级线 Service业务层处理（rule-eval 形状）
 *
 * <p>归层的算法全司只有这一处，屏上摆出第二套就算错。四问口径钉死在本类，
 * 谁也不许替它另抓一把尺子凑出一层来；先钉住层数再回头配一条线的次序，倒过来不走：</p>
 * <ol>
 *   <li><b>在位线怎么回取</b>：未删、未撤、起用那一日（含）已经过去、交班那一日（不含）还没到；
 *       旧册手抄线交班那一格空着的，一律当一直在位。唯一出处见 {@link #inPositionAt}。</li>
 *   <li><b>三档都够不上给不给说法</b>：给——判档 4「不提醒」，回话里线代号与起用那一日两格留空；
 *       不许照旧退回，更不许抄近旁那条线的数字拼一个看着体面的答复。见 {@link #levelOf}。</li>
 *   <li><b>两条并序时听谁的</b>：交班次序（priority）数值大的先开口；次序并列比线代号，
 *       字面序排在后头的那条赢；再并列按主键兜底——同一时刻只许问得出一层。见 {@link #SPEAK_ORDER}。</li>
 *   <li><b>压线往哪头落</b>：日数正好踩在分界上朝紧的一头归（一级最紧）；
 *       取数与比数是两道工，先照同一把舍法（统一 2 位、四舍五入）把数取齐，取齐之后才许贴线。
 *       见 {@link #qi}。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreWarnLineServiceImpl implements ITSecreWarnLineService {

    /**
     * 并列裁决（确定性，同一时刻只许问得出一层）：
     * 交班次序数值大的先开口；次序并列按线代号字面序，排在后头的那条赢；再并列按主键兜底。
     */
    private static final Comparator<TSecreWarnLine> SPEAK_ORDER = new Comparator<TSecreWarnLine>() {
        @Override
        public int compare(TSecreWarnLine a, TSecreWarnLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TSecreWarnLineMapper secreWarnLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 同一把舍法（精度统一 2 位、四舍五入）：取数与比数是两道工，
     * 先照它把数取齐，取齐之后才许贴线——取的时候进了一位、比的时候没进，层就归错了。
     */
    private static BigDecimal qi(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 在位口径唯一出处（屏上名单、捋册名单、回取、计数、旁路 usable 全从这一处算）：
     * 未删、未撤、起用那一日（含）已经过去、交班那一日（不含）还没到；
     * 旧册手抄线交班那一格空着的，一律当一直在位——兼容这道口子不能松。
     */
    private boolean inPositionAt(TSecreWarnLine r, String atTs) {
        if (r == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false; // 删了的，册里已查不着
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            return false; // 撤下的那一条：册里查得着，但不配替当下答话
        }
        if (r.getEffStart() == null) {
            return false; // 起用那一日空着，证明不了已经起用
        }
        if (ts(r.getEffStart()).compareTo(atTs) > 0) {
            return false; // 起用那一日还没过去
        }
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(atTs) <= 0) {
            return false; // 交班那一日（不含）一到即交班：该日之前归旧线，该日起归新线
        }
        return true; // 交班空着（旧册手抄）= 一直在位
    }

    @Override
    public TSecreWarnLine selectTSecreWarnLineById(Long id) {
        return this.secreWarnLineMapper.selectById(id);
    }

    @Override
    public List<TSecreWarnLine> listAvailable(Date at) {
        List<TSecreWarnLine> avail = new ArrayList<TSecreWarnLine>();
        if (at == null) {
            return avail;
        }
        String atTs = ts(at);
        // 把线册从头捋一遍，逐条过同一个在位口径——屏上那份名单与这份名单来自同一回计算
        for (TSecreWarnLine r : this.secreWarnLineMapper.selectList(new QueryWrapper<TSecreWarnLine>())) {
            if (inPositionAt(r, atTs)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, SPEAK_ORDER);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        TSecreWarnLine rule = findInPosition(ruleCode, at);
        if (rule == null) {
            return 0; // 发问当刻请不出在位线（不许拿此刻最新那一条替旧件答话）
        }
        if (!checkLine(rule).isEmpty()) {
            return 0; // 这条线当下抬不出来（空的是哪一格、哪一格没盖过前头，见 checkLine 的回话）
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        List<TSecreWarnLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0; // 当刻没有在位线可开口
        }
        TSecreWarnLine speaker = avail.get(0);
        if (!checkLine(speaker).isEmpty()) {
            return 0; // 可开口说话的只有那一条；它抬不出来，其余几条不许替当下答话
        }
        return levelOf(speaker, input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        return inPositionAt(this.secreWarnLineMapper.selectById(id), ts(at));
    }

    @Override
    public int countAvailable(Date at) {
        // 与 listAvailable 同一回计算，不许另算一份
        return listAvailable(at).size();
    }

    @Override
    public TSecreWarnLine findInPosition(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        // 在位名单已按并列裁决顺序排好；同一代号的几条版本换线日首尾相接，当刻在位的只此一条
        for (TSecreWarnLine r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                return r;
            }
        }
        return null;
    }

    @Override
    public List<String> checkLine(TSecreWarnLine line) {
        List<String> problems = new ArrayList<String>();
        if (line == null) {
            problems.add("线本身空着，册里查不着");
            return problems;
        }
        BigDecimal t1 = qi(line.getTh1Max());
        BigDecimal t2 = qi(line.getTh2Max());
        BigDecimal t3 = qi(line.getTh3Max());
        if (t1 == null) {
            problems.add("起算日数(th1_max)这一格空着");
        }
        if (t2 == null) {
            problems.add("够线日数(th2_max)这一格空着");
        }
        if (t3 == null) {
            problems.add("封顶日数(th3_max)这一格空着");
        }
        if (t1 != null && t2 != null && t2.compareTo(t1) <= 0) {
            problems.add("够线日数(th2_max)没能盖过它前头的起算日数(th1_max)");
        }
        if (t2 != null && t3 != null && t3.compareTo(t2) <= 0) {
            problems.add("封顶日数(th3_max)没能盖过它前头的够线日数(th2_max)");
        }
        return problems;
    }

    /**
     * 分档（一级最紧、三级最松）：日数正好踩在分界上朝紧的一头归（&lt;=）；
     * 三档一条也没贴着 = 4「不提醒」，回话里线代号与起用那一日两格留空。
     * 调用前须先经 {@link #checkLine} 验过三格齐全且递增（抬得出来的线才许贴）。
     */
    private int levelOf(TSecreWarnLine rule, BigDecimal input) {
        BigDecimal in = qi(input);
        if (in.compareTo(qi(rule.getTh1Max())) <= 0) {
            return 1;
        }
        if (in.compareTo(qi(rule.getTh2Max())) <= 0) {
            return 2;
        }
        if (in.compareTo(qi(rule.getTh3Max())) <= 0) {
            return 3;
        }
        return 4;
    }
}
