package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.model.custom.WarnLevelResult;
import com.fc.v2.service.impl.TSecreWarnLineServiceImpl;

/**
 * 届期预警分级线口径测试：请线时点、换线日不含、并列裁决、压线归紧、
 * 取数比数同一把舍法、不提醒留空、撤线残线剔除、旧册空交班、两通道同层。
 */
@ExtendWith(MockitoExtension.class)
public class TSecreWarnLineServiceTest {

    @Mock
    private TSecreWarnLineMapper mapper;

    private TSecreWarnLineServiceImpl service;

    private final List<TSecreWarnLine> archive = new ArrayList<TSecreWarnLine>();

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 一条齐整在位线：三格 10/20/30，时段可指定，交班 null 即旧册一直在位 */
    private static TSecreWarnLine line(String code, int priority, String start, String end,
                                      String th1, String th2, String th3, int status) {
        TSecreWarnLine r = new TSecreWarnLine();
        r.setRuleCode(code);
        r.setRuleName("线" + code);
        r.setPriority(priority);
        r.setEffStart(start == null ? null : t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @BeforeEach
    public void setUp() {
        service = new TSecreWarnLineServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "secreWarnLineMapper", mapper);
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(inv -> filterByCode(inv.getArgument(0), archive));
    }

    /** 仿库侧 eq("rule_code", code)：包装器里出现的字符串参数按代号过滤（del_flag 等数值参数忽略） */
    @SuppressWarnings("unchecked")
    private static List<TSecreWarnLine> filterByCode(Wrapper<TSecreWarnLine> w, List<TSecreWarnLine> src) {
        List<TSecreWarnLine> out = new ArrayList<TSecreWarnLine>(src);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TSecreWarnLine> qw =
                (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TSecreWarnLine>) w;
        qw.getSqlSegment();
        for (Object v : qw.getParamNameValuePairs().values()) {
            if (v instanceof String) {
                final String code = (String) v;
                out.removeIf(r -> !code.equals(r.getRuleCode()));
            }
        }
        return out;
    }

    /** 请线那一手：补判旧批件请的是发问当下在位的线，不是此刻最新那一条 */
    @Test
    public void asksLineInPositionAtAskTime_notNewest() {
        archive.add(line("OLD", 1, "2024-01-01 00:00:00", "2025-01-01 00:00:00", "10", "20", "30", 0));
        archive.add(line("NEW", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));

        WarnLevelResult oldAsk = service.resolveWarn(new BigDecimal("5"), t("2024-06-01 08:00:00"));
        assertEquals(1, oldAsk.getLevel());
        assertEquals("OLD", oldAsk.getRuleCode());

        WarnLevelResult newAsk = service.resolveWarn(new BigDecimal("5"), t("2025-06-01 08:00:00"));
        assertEquals("NEW", newAsk.getRuleCode());
    }

    /** 换线那一日唯一说法：之前归旧线，该日 00:00:00 起归新线（交班不含） */
    @Test
    public void handoverDayExclusive_sameMomentConsistent() {
        archive.add(line("OLD", 1, "2024-01-01 00:00:00", "2025-01-01 00:00:00", "10", "20", "30", 0));
        archive.add(line("NEW", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));

        Date lastMoment = t("2024-12-31 23:59:59");
        Date firstMoment = t("2025-01-01 00:00:00");
        assertEquals("OLD", service.resolveWarn(new BigDecimal("5"), lastMoment).getRuleCode());
        assertEquals("NEW", service.resolveWarn(new BigDecimal("5"), firstMoment).getRuleCode());
        // 同一时刻两个通道（只问 / 顶头取值）必须同一层
        assertEquals(service.resolveWarn(new BigDecimal("15"), firstMoment).getLevel(),
                service.evaluateTop(new BigDecimal("15"), firstMoment));
        assertEquals(2, service.evaluateTop(new BigDecimal("15"), firstMoment));
    }

    /** 起用那一日含当日：起用当天 00:00:00 已在位 */
    @Test
    public void effStartInclusive() {
        TSecreWarnLine s = line("S", 1, "2025-03-01 00:00:00", null, "10", "20", "30", 0);
        archive.add(s);
        when(mapper.selectById(1L)).thenReturn(s);
        assertTrue(service.usable(1L, t("2025-03-01 00:00:00")));
        assertEquals("S", service.resolveWarn(new BigDecimal("5"), t("2025-03-01 00:00:00")).getRuleCode());
        assertNull(service.resolveWarn(new BigDecimal("5"), t("2025-02-28 23:59:59")).getLevel());
    }

    /** 并列那一手：交班次序大的先开口 */
    @Test
    public void tieBreakByPriorityDesc() {
        archive.add(line("B", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        archive.add(line("A", 2, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        assertEquals("A", service.activeAt(t("2025-06-01 00:00:00")).getRuleCode());
    }

    /** 次序并列比代号：照字面序排在后头的那条赢（B 在 A 后头） */
    @Test
    public void tieBreakByCodeLiteralDesc() {
        archive.add(line("A", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        archive.add(line("B", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        assertEquals("B", service.activeAt(t("2025-06-01 00:00:00")).getRuleCode());
        // 字面序不认数字大小：WL10 排在 WL09 后头（'1' > '0'）
        archive.clear();
        archive.add(line("WL09", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        archive.add(line("WL10", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        assertEquals("WL10", service.activeAt(t("2025-06-01 00:00:00")).getRuleCode());
    }

    /** 压线那一手：正好踩分界朝紧的一头归，且两个通道同一层 */
    @Test
    public void boundaryGoesTight_twoChannelsSame() {
        archive.add(line("S", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        Date at = t("2025-06-01 00:00:00");
        assertEquals(1, service.resolveWarn(new BigDecimal("10"), at).getLevel());
        assertEquals(2, service.resolveWarn(new BigDecimal("20"), at).getLevel());
        assertEquals(3, service.resolveWarn(new BigDecimal("30"), at).getLevel());
        assertEquals(1, service.evaluate("S", new BigDecimal("10"), at));
        assertEquals(2, service.evaluate("S", new BigDecimal("20"), at));
        assertEquals(3, service.evaluate("S", new BigDecimal("30"), at));
    }

    /**
     * 比数那一手：取数比数同一把舍法（3 位 HALF_UP）。10.0006 取齐为 10.001，已过起算 10，
     * 该提一级到二级；取时进位、比时不进的两把尺会把它错按在一级。
     */
    @Test
    public void sameRoundingForFetchAndCompare() {
        archive.add(line("S", 1, "2025-01-01 00:00:00", null, "10.000", "20.000", "30.000", 0));
        WarnLevelResult r = service.resolveWarn(new BigDecimal("10.0006"), t("2025-06-01 00:00:00"));
        assertEquals(2, r.getLevel());
        // 10.0004 取齐为 10.000，正好压线，朝紧的一头归一级
        assertEquals(1, service.resolveWarn(new BigDecimal("10.0004"), t("2025-06-01 00:00:00")).getLevel());
    }

    /** 够不上那一手：超封顶 / 册里无在位线 → 不提醒，代号与起用日一并留空 */
    @Test
    public void noneWhenBeyondCapOrNoActive() {
        archive.add(line("S", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0));
        WarnLevelResult beyond = service.resolveWarn(new BigDecimal("30.001"), t("2025-06-01 00:00:00"));
        assertFalse(beyond.isRemind());
        assertEquals("不提醒", beyond.getLevelText());
        assertNull(beyond.getRuleCode());
        assertNull(beyond.getEffDate());

        WarnLevelResult noLine = service.resolveWarn(new BigDecimal("5"), t("2024-01-01 00:00:00"));
        assertFalse(noLine.isRemind());
        assertNull(noLine.getRuleCode());
    }

    /** 三格空一格抬不出来：回话点名空的是哪一格 */
    @Test
    public void missingThresholdNamed() {
        String defect = service.thresholdDefect(new BigDecimal("10"), null, new BigDecimal("30"));
        assertTrue(defect.contains("够线日数这一格空着"), defect);
        archive.add(line("X", 1, "2025-01-01 00:00:00", null, "10", null, "30", 0));
        Date at = t("2025-06-01 00:00:00");
        assertEquals(0, service.countAvailable(at));
        assertNull(service.activeAt(at));
    }

    /** 排后面的压不过排前面的：点名是哪一格没能盖过它前头那一个，线也抬不出来 */
    @Test
    public void nonMonotonicNamedAndExcluded() {
        String d1 = service.thresholdDefect(new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("30"));
        assertTrue(d1.contains("够线日数(20)没能盖过它前头的起算日数(20)"), d1);
        String d2 = service.thresholdDefect(new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("30"));
        assertTrue(d2.contains("封顶日数(30)没能盖过它前头的够线日数(40)"), d2);

        archive.add(line("X", 1, "2025-01-01 00:00:00", null, "20", "20", "30", 0));
        assertNull(service.activeAt(t("2025-06-01 00:00:00")));
    }

    /** 撤过的那一条：册里查得着，现行那排里不许数上它，也不许缀在最新那句后头 */
    @Test
    public void revokedExcluded_evenAlone() {
        archive.add(line("DEAD", 9, "2020-01-01 00:00:00", null, "10", "20", "30", 1));
        Date at = t("2025-06-01 00:00:00");
        assertEquals(0, service.countAvailable(at));
        assertNull(service.activeAt(at));
        assertFalse(service.listAvailable(at).stream().anyMatch(r -> "DEAD".equals(r.getRuleCode())));
        assertEquals(0, service.evaluate("DEAD", new BigDecimal("5"), at));
    }

    /** 删掉的（del_flag=1）同样不数 */
    @Test
    public void deletedExcluded() {
        TSecreWarnLine gone = line("G", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0);
        gone.setDelFlag(1);
        archive.add(gone);
        assertEquals(0, service.countAvailable(t("2025-06-01 00:00:00")));
    }

    /** 旧册那一手：交班格空着只写起用日的手抄线，一律当一直在位 */
    @Test
    public void legacyLineNullEffEndStaysForever() {
        TSecreWarnLine oldLine = line("OLD", 1, "2001-01-01 00:00:00", null, "10", "20", "30", 0);
        archive.add(oldLine);
        when(mapper.selectById(1L)).thenReturn(oldLine);
        Date at = t("2025-06-01 00:00:00");
        assertEquals("OLD", service.activeAt(at).getRuleCode());
        assertTrue(service.usable(1L, at));
    }

    /** 旧算法形状照旧：按代号也只能取发问当下在位的那一版，不拿最新一条判旧件 */
    @Test
    public void legacyEvaluateByCodeUsesInPositionVersion() {
        archive.add(line("R", 1, "2024-01-01 00:00:00", "2025-01-01 00:00:00", "10", "20", "30", 0));
        archive.add(line("R", 1, "2025-01-01 00:00:00", null, "60", "90", "120", 0));
        // 旧批件发问在 2024：35 日已超旧版封顶不提醒；新版起算 60，若错拿新版会误挂一级
        assertEquals(0, service.evaluate("R", new BigDecimal("35"), t("2024-06-01 00:00:00")));
        assertEquals(1, service.evaluate("R", new BigDecimal("35"), t("2025-06-01 00:00:00")));
        // 撤下的代号查旧算法也是 0
        archive.clear();
        archive.add(line("Z", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 1));
        assertEquals(0, service.evaluate("Z", new BigDecimal("5"), t("2025-06-01 00:00:00")));
    }

    /** 屏上名单与全捋名单同源：listAvailable 与 activeAt 同回计算，裁决头一条就是名单头一条 */
    @Test
    public void screenListAndFullSweepSameSource() {
        archive.addAll(Arrays.asList(
                line("C", 1, "2025-01-01 00:00:00", null, "10", "20", "30", 0),
                line("A", 2, "2024-01-01 00:00:00", "2026-01-01 00:00:00", "10", "20", "30", 0),
                line("B", 2, "2024-01-01 00:00:00", null, "10", "20", "30", 0)));
        List<TSecreWarnLine> screen = service.listAvailable(t("2025-06-01 00:00:00"));
        assertEquals(3, screen.size());
        // 次序 2 的两条排前，代号 B 字面在 A 后头 → B 先；C 次序 1 垫后
        assertEquals(Arrays.asList("B", "A", "C"), Arrays.asList(
                screen.get(0).getRuleCode(), screen.get(1).getRuleCode(), screen.get(2).getRuleCode()));
        assertEquals(screen.get(0).getRuleCode(), service.activeAt(t("2025-06-01 00:00:00")).getRuleCode());
    }
}
