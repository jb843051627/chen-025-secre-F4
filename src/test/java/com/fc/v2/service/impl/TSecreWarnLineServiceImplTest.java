package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreWarnLine;

/**
 * 届期预警分级线口径回归：归层算法全司只有一套，四问只认一处给出的说法。
 */
public class TSecreWarnLineServiceImplTest {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private TSecreWarnLineServiceImpl service;
    private List<TSecreWarnLine> register;

    @BeforeEach
    public void setUp() {
        TSecreWarnLineMapper mapper = mock(TSecreWarnLineMapper.class);
        service = new TSecreWarnLineServiceImpl();
        ReflectionTestUtils.setField(service, "secreWarnLineMapper", mapper);
        register = new ArrayList<TSecreWarnLine>();
        when(mapper.selectList(any())).thenAnswer(inv -> new ArrayList<TSecreWarnLine>(register));
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Object id = inv.getArgument(0);
            for (TSecreWarnLine r : register) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
    }

    private TSecreWarnLine line(long id, String code, int priority, Integer status,
            String th1, String th2, String th3, String start, String end) throws Exception {
        TSecreWarnLine r = new TSecreWarnLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(start == null ? null : FMT.parse(start));
        r.setEffEnd(end == null ? null : FMT.parse(end));
        return r;
    }

    private Date at(String ts) throws Exception {
        return FMT.parse(ts);
    }

    private BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ---------- 比数那一手 ----------

    @Test
    public void 比数_三格空一格当下抬不出来_回话点明哪一格() throws Exception {
        register.add(line(1L, "L1", 1, 0, "30", null, "90", "2026-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        assertEquals(0, service.evaluateTop(bd("25"), at));
        assertEquals(0, service.evaluate("L1", bd("25"), at));
        List<String> reply = service.checkLine(register.get(0));
        assertEquals(1, reply.size());
        assertTrue(reply.get(0).contains("够线日数(th2_max)"), "回话须点明空的是哪一格: " + reply);
    }

    @Test
    public void 比数_后一格压不过前一格_抬不出来_回话点明哪一格() throws Exception {
        register.add(line(1L, "L1", 1, 0, "30", "30", "90", "2026-01-01 00:00:00", null));
        register.add(line(2L, "L2", 1, 0, "30", "60", "60", "2026-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        assertEquals(0, service.evaluate("L1", bd("25"), at));
        assertEquals(0, service.evaluate("L2", bd("25"), at));
        List<String> r1 = service.checkLine(register.get(0));
        assertTrue(r1.size() == 1 && r1.get(0).contains("够线日数(th2_max)没能盖过"), "须点明哪一格没盖过前头: " + r1);
        List<String> r2 = service.checkLine(register.get(1));
        assertTrue(r2.size() == 1 && r2.get(0).contains("封顶日数(th3_max)没能盖过"), "须点明哪一格没盖过前头: " + r2);
    }

    @Test
    public void 比数_取数与比数同一把舍法_取齐之后才贴线() throws Exception {
        register.add(line(1L, "L1", 1, 0, "30", "60", "90", "2026-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        // 同一把舍法取齐到 2 位：30.004 -> 30.00 压线朝紧归一级；30.005 -> 30.01 落二级
        assertEquals(1, service.evaluateTop(bd("30.004"), at));
        assertEquals(2, service.evaluateTop(bd("30.005"), at));
        assertEquals(2, service.evaluateTop(bd("60.004"), at));
        assertEquals(3, service.evaluateTop(bd("60.005"), at));
        assertEquals(3, service.evaluateTop(bd("90.004"), at));
        assertEquals(4, service.evaluateTop(bd("90.005"), at));
    }

    // ---------- 请线那一手 ----------

    @Test
    public void 请线_补判旧件用各自发问当下在位的线() throws Exception {
        // 旧年立的线管到 2025 元旦（不含），今年新立的从 2025 元旦起管
        register.add(line(1L, "OLD", 1, 0, "10", "20", "30", "2024-01-01 00:00:00", "2025-01-01 00:00:00"));
        register.add(line(2L, "NEW", 1, 0, "50", "60", "70", "2025-01-01 00:00:00", null));
        // 前年那批发问的件，须照发问当下在位的旧线判（25 -> 三级），不许拿此刻最新那条新线判成一级
        assertEquals(3, service.evaluateTop(bd("25"), at("2024-06-01 00:00:00")));
        assertEquals(1, service.evaluateTop(bd("25"), at("2025-06-01 00:00:00")));
        // 按线代号回取同样只认发问当刻在位的版本
        assertEquals(3, service.evaluate("OLD", bd("25"), at("2024-06-01 00:00:00")));
        assertEquals(0, service.evaluate("OLD", bd("25"), at("2025-06-01 00:00:00")), "旧线已交班，请不出在位线");
    }

    @Test
    public void 请线_换线日只许一种说法_同一时刻两遍同层() throws Exception {
        register.add(line(1L, "OLD", 1, 0, "10", "20", "30", "2024-01-01 00:00:00", "2025-01-01 00:00:00"));
        register.add(line(2L, "NEW", 1, 0, "50", "60", "70", "2025-01-01 00:00:00", null));
        // 交班当日（不含）之前归旧线，当日起往后归新线
        assertEquals(3, service.evaluateTop(bd("25"), at("2024-12-31 23:59:59")));
        assertEquals(1, service.evaluateTop(bd("25"), at("2025-01-01 00:00:00")));
        // 前后同一时刻各问一遍，两遍必须给出同一层
        Date moment = at("2025-01-01 00:00:00");
        assertEquals(service.evaluateTop(bd("25"), moment), service.evaluateTop(bd("25"), moment));
    }

    @Test
    public void 请线_撤下的那一条不配定层_也不许缀在最新那句后头() throws Exception {
        // 撤过的线：册里查得着，但交班次序再高、阈值再紧也不配替当下答话
        register.add(line(1L, "REV", 9, 1, "1", "2", "3", "2020-01-01 00:00:00", null));
        register.add(line(2L, "ACT", 1, 0, "50", "60", "70", "2020-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        assertEquals(1, service.evaluateTop(bd("25"), at), "须由在位的那条答话，不许拿撤下的凑");
        assertEquals(1, service.listAvailable(at).size());
        assertNull(service.findInPosition("REV", at));
        assertFalse(service.usable(1L, at));
        assertTrue(service.usable(2L, at));
        // 册里只剩撤下的线时，当刻请不出在位线
        register.remove(1);
        assertEquals(0, service.evaluateTop(bd("25"), at));
    }

    @Test
    public void 请线_旧册手抄线交班空着一律当一直在位() throws Exception {
        // 早年手抄进来的线：只写了起用那一天，交班那一格空着
        TSecreWarnLine legacy = line(1L, "LEG", 1, 0, "30", "60", "90", "2000-01-01 00:00:00", null);
        legacy.setStatus(null); // 手抄行连情形格也常空着，按在册认
        legacy.setDelFlag(null);
        register.add(legacy);
        Date at = at("2026-10-05 00:00:00");
        assertEquals(2, service.evaluateTop(bd("45"), at));
        assertTrue(service.usable(1L, at));
        assertEquals(1, service.countAvailable(at));
    }

    // ---------- 并列那一手 ----------

    @Test
    public void 并列_交班次序大的先开口_次序并列字面序后者赢() throws Exception {
        Date at = at("2026-03-01 00:00:00");
        // 交班次序数值大的先开口
        register.add(line(1L, "RA", 1, 0, "10", "20", "30", "2026-01-01 00:00:00", null));
        register.add(line(2L, "RB", 2, 0, "50", "60", "70", "2026-01-01 00:00:00", null));
        assertEquals(1, service.evaluateTop(bd("25"), at), "RB 次序在前，须由 RB 开口");
        assertEquals("RB", service.listAvailable(at).get(0).getRuleCode());
        // 次序并列，比线代号，字面序排在后头的那条赢
        register.get(1).setPriority(1);
        assertEquals(1, service.evaluateTop(bd("25"), at), "并列时 RB 字面序在后，须由 RB 开口");
        // 同一时刻问不出两个层级
        assertEquals(service.evaluateTop(bd("25"), at), service.evaluateTop(bd("25"), at));
        assertEquals(service.listAvailable(at).get(0).getRuleCode(), service.listAvailable(at).get(0).getRuleCode());
    }

    // ---------- 够不上那一手 ----------

    @Test
    public void 够不上_落不提醒_不许退回也不许凑数() throws Exception {
        register.add(line(1L, "L1", 1, 0, "30", "60", "90", "2026-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        assertEquals(4, service.evaluateTop(bd("90.01"), at), "三档够不上须给说法：不提醒(4)");
        assertEquals(4, service.evaluate("L1", bd("90.01"), at));
        assertEquals(4, service.evaluateTop(bd("150"), at), "不许自编上限把该落不提醒的件退回去");
        assertEquals(1, service.evaluateTop(bd("-1"), at), "已越过届期的件最紧，归一级");
        // 当刻一条在位线也没有，才回 0
        register.clear();
        assertEquals(0, service.evaluateTop(bd("25"), at));
    }

    @Test
    public void 够不上_屏上挂着线代号却请不出在位线的件能挑出来() throws Exception {
        register.add(line(1L, "GONE", 1, 0, "30", "60", "90", "2020-01-01 00:00:00", "2021-01-01 00:00:00"));
        Date now = at("2026-03-01 00:00:00");
        assertNull(service.findInPosition("GONE", now), "挂着 GONE 的件当刻请不出在位线，须能一单一单挑出来");
        assertEquals(0, service.evaluate("GONE", bd("25"), now));
    }

    // ---------- 压线那一手 ----------

    @Test
    public void 压线_正好踩在分界上朝紧的一头归_两个通道同一层() throws Exception {
        register.add(line(1L, "L1", 1, 0, "30", "60", "90", "2026-01-01 00:00:00", null));
        Date at = at("2026-03-01 00:00:00");
        assertEquals(1, service.evaluateTop(bd("30"), at));
        assertEquals(2, service.evaluateTop(bd("60"), at));
        assertEquals(3, service.evaluateTop(bd("90"), at));
        // 从两个通道分别问它，回话必须是同一层
        for (String v : new String[] { "30", "60", "90", "45", "90.01" }) {
            assertEquals(service.evaluate("L1", bd(v), at), service.evaluateTop(bd(v), at),
                    "两个通道对同一时刻同一件必须同层: " + v);
        }
    }

    @Test
    public void 压线_屏上名单与捋册名单同一回计算() throws Exception {
        Date at = at("2026-03-01 00:00:00");
        register.add(line(1L, "A", 1, 0, "30", "60", "90", "2020-01-01 00:00:00", null)); // 在位
        register.add(line(2L, "B", 1, 0, "30", "60", "90", "2030-01-01 00:00:00", null)); // 起用日未到
        register.add(line(3L, "C", 1, 0, "30", "60", "90", "2020-01-01 00:00:00", "2021-01-01 00:00:00")); // 已交班
        register.add(line(4L, "D", 1, 1, "30", "60", "90", "2020-01-01 00:00:00", null)); // 已撤下
        register.add(line(5L, "E", 1, 0, "30", "60", "90", "2000-01-01 00:00:00", null)); // 旧册一直在位
        register.add(line(6L, "F", 1, 0, "30", "60", "90", "2020-01-01 00:00:00", null)); // 已删除
        register.get(5).setDelFlag(1);
        assertEquals(2, service.listAvailable(at).size());
        assertEquals(service.listAvailable(at).size(), service.countAvailable(at), "两个名单得来自同一回计算");
        for (TSecreWarnLine r : register) {
            boolean inList = service.listAvailable(at).contains(r);
            assertEquals(inList, service.usable(r.getId(), at), "usable 口径须与名单一致: " + r.getRuleCode());
        }
    }

    // ---------- 旧册那一手：钉牢的那一对只认一处 ----------

    @Test
    public void 钉牢_按线代号回取的须是发问当刻在位的那一条() throws Exception {
        register.add(line(1L, "W", 1, 0, "10", "20", "30", "2024-01-01 00:00:00", "2025-01-01 00:00:00"));
        register.add(line(2L, "W", 1, 0, "50", "60", "70", "2025-01-01 00:00:00", null));
        TSecreWarnLine y24 = service.findInPosition("W", at("2024-06-01 00:00:00"));
        assertEquals(Long.valueOf(1L), y24.getId(), "2024 年发问须钉 2024 版那一对");
        assertEquals("W", y24.getRuleCode());
        assertEquals(FMT.parse("2024-01-01 00:00:00"), y24.getEffStart());
        TSecreWarnLine y26 = service.findInPosition("W", at("2026-06-01 00:00:00"));
        assertEquals(Long.valueOf(2L), y26.getId(), "2026 年发问须钉 2025 版那一对，不许拿去年那季的线来配");
        assertNull(service.findInPosition("W", at("2023-06-01 00:00:00")), "线在 2023 年还没起用");
        assertNull(service.findInPosition("NOPE", at("2026-06-01 00:00:00")));
    }

    @Test
    public void 抬不出来_该开口的那一条缺格_其余几条不许替当下答话() throws Exception {
        // 裁决出的头一条缺了格，当下就抬不出来；次序在后的线不许顶包凑一层
        register.add(line(1L, "HEAD", 2, 0, "30", null, "90", "2026-01-01 00:00:00", null));
        register.add(line(2L, "NEXT", 1, 0, "30", "60", "90", "2026-01-01 00:00:00", null));
        assertEquals(0, service.evaluateTop(bd("25"), at("2026-03-01 00:00:00")));
    }
}
