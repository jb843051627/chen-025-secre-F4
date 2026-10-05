package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.model.custom.StaleWarnStamp;
import com.fc.v2.model.custom.WarnLevelResult;
import com.fc.v2.service.impl.TSecreCarrierServiceImpl;
import com.fc.v2.service.impl.TSecreWarnLineServiceImpl;

/**
 * 载体钉档与挑单测试：层级落行时线代号与起用日一并钉牢；不提醒三格一并清空；
 * 挂着线代号却请不出当下在位线的件一单一单挑出。
 */
@ExtendWith(MockitoExtension.class)
public class TSecreCarrierServiceTest {

    @Mock
    private TSecreCarrierMapper carrierMapper;

    @Mock
    private TSecreWarnLineMapper warnLineMapper;

    private TSecreCarrierServiceImpl carrierService;

    private final List<TSecreWarnLine> lineArchive = new ArrayList<TSecreWarnLine>();

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TSecreWarnLine line(String code, String start, String end, int status,
                                      String th1, String th2, String th3) {
        TSecreWarnLine r = new TSecreWarnLine();
        r.setRuleCode(code);
        r.setRuleName("线" + code);
        r.setPriority(1);
        r.setEffStart(t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setStatus(status);
        r.setDelFlag(0);
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        return r;
    }

    private static TSecreCarrier carrier(long id, Integer level, String code, String effDate) {
        TSecreCarrier c = new TSecreCarrier();
        c.setId(id);
        c.setCarrierNo("C" + id);
        c.setDelFlag(0);
        c.setWarnLevel(level);
        c.setWarnRuleCode(code);
        c.setWarnEffDate(effDate == null ? null : t(effDate));
        return c;
    }

    @BeforeEach
    public void setUp() {
        TSecreWarnLineServiceImpl engine = new TSecreWarnLineServiceImpl();
        ReflectionTestUtils.setField(engine, "secreWarnLineMapper", warnLineMapper);

        carrierService = new TSecreCarrierServiceImpl();
        ReflectionTestUtils.setField(carrierService, "baseMapper", carrierMapper);
        ReflectionTestUtils.setField(carrierService, "secreWarnLineMapper", warnLineMapper);
        ReflectionTestUtils.setField(carrierService, "secreWarnLineService", engine);

        org.mockito.Mockito.lenient().when(warnLineMapper.selectList(any(Wrapper.class)))
                .thenAnswer(inv -> filterByCode(inv.getArgument(0), lineArchive));
        // 只有挑单用例查载体列表，放宽以避免多余插桩报错
        org.mockito.Mockito.lenient().when(carrierMapper.selectList(any(Wrapper.class)))
                .thenReturn(new ArrayList<TSecreCarrier>());
    }

    /** 仿库侧 eq("rule_code", code) 过滤；本版 MP 在渲染 SQL 片段时才把参数放进 map，先触发一下 */
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

    /** 仿库执行 UpdateWrapper：按 sqlSet 里每列自带的参数键取值（含 NULL 清空），不碰 where 参数 */
    @SuppressWarnings("unchecked")
    private static void applyPatch(TSecreCarrier c, Wrapper<TSecreCarrier> w) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSecreCarrier> uw =
                (com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TSecreCarrier>) w;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w+)=#\\{ew\\.paramNameValuePairs\\.(\\w+)}")
                .matcher(uw.getSqlSet());
        while (m.find()) {
            String col = m.group(1);
            Object v = uw.getParamNameValuePairs().get(m.group(2));
            if ("warn_level".equals(col)) {
                c.setWarnLevel(v == null ? null : ((Number) v).intValue());
            } else if ("warn_rule_code".equals(col)) {
                c.setWarnRuleCode(v == null ? null : v.toString());
            } else if ("warn_eff_date".equals(col)) {
                c.setWarnEffDate(v == null ? null : (Date) v);
            }
        }
    }

    /** 钉档：层级落行的同时把线代号与起用那一日一并钉牢，回话与落行是同一对 */
    @Test
    public void applyWarn_nailsLevelCodeAndEffDate() {
        lineArchive.add(line("R", "2025-01-01 00:00:00", null, 0, "10", "20", "30"));
        TSecreCarrier c = carrier(1L, null, null, null);
        when(carrierMapper.selectOne(any(Wrapper.class))).thenReturn(c);
        doAnswer(inv -> {
            applyPatch(c, inv.getArgument(1));
            return 1;
        }).when(carrierMapper).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));

        WarnLevelResult result =
                carrierService.applyWarn(1L, new BigDecimal("5"), t("2025-06-01 08:00:00"));
        assertEquals(1, result.getLevel().intValue());
        assertEquals("R", result.getRuleCode());
        assertEquals(1, c.getWarnLevel().intValue());
        assertEquals("R", c.getWarnRuleCode());
        assertEquals("2025-01-01 00:00:00", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(c.getWarnEffDate()));
    }

    /** 不提醒：回话那三个字，线代号与起用日两格留空，落行也三格一并清空（不许留半拉钉子） */
    @Test
    public void applyWarn_noneClearsAllThree() {
        lineArchive.add(line("R", "2025-01-01 00:00:00", null, 0, "10", "20", "30"));
        TSecreCarrier c = carrier(2L, 2, "OLDSTAMP", "2024-01-01 00:00:00");
        when(carrierMapper.selectOne(any(Wrapper.class))).thenReturn(c);
        doAnswer(inv -> {
            applyPatch(c, inv.getArgument(1));
            return 1;
        }).when(carrierMapper).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));

        WarnLevelResult result =
                carrierService.applyWarn(2L, new BigDecimal("99"), t("2025-06-01 08:00:00"));
        assertFalse(result.isRemind());
        assertEquals("不提醒", result.getLevelText());
        assertNull(c.getWarnLevel());
        assertNull(c.getWarnRuleCode());
        assertNull(c.getWarnEffDate());
    }

    /** 补判旧批件请旧版线：钉的是发问当下在位那版，不是此刻最新那一条 */
    @Test
    public void applyWarn_historicalAskStampsOldVersion() {
        lineArchive.add(line("R", "2024-01-01 00:00:00", "2025-01-01 00:00:00", 0, "10", "20", "30"));
        lineArchive.add(line("R", "2025-01-01 00:00:00", null, 0, "60", "90", "120"));
        TSecreCarrier c = carrier(3L, null, null, null);
        when(carrierMapper.selectOne(any(Wrapper.class))).thenReturn(c);
        doAnswer(inv -> {
            applyPatch(c, inv.getArgument(1));
            return 1;
        }).when(carrierMapper).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));

        carrierService.applyWarn(3L, new BigDecimal("35"), t("2024-06-01 08:00:00"));
        // 35 日在旧版超封顶 → 不提醒；绝不能拿新版（起算60）错挂一级
        assertNull(c.getWarnLevel());
        assertNull(c.getWarnRuleCode());
    }

    /** 挑单：查无此线／已撤下／不在时段／起用日合不拢／钉档残缺 各自一单一单落出来，好件不进单 */
    @Test
    public void staleStamps_eachReasonPickedIndividually() {
        // 册里：R 有新旧两版；DEAD 已撤；good 线当下在位
        lineArchive.add(line("R", "2024-01-01 00:00:00", "2025-01-01 00:00:00", 0, "10", "20", "30"));
        lineArchive.add(line("R", "2025-01-01 00:00:00", null, 0, "60", "90", "120"));
        lineArchive.add(line("DEAD", "2020-01-01 00:00:00", null, 1, "10", "20", "30"));
        lineArchive.add(line("GOOD", "2025-01-01 00:00:00", null, 0, "10", "20", "30"));

        List<TSecreCarrier> rows = new ArrayList<TSecreCarrier>();
        rows.add(carrier(10L, 1, "GHOST", "2025-01-01 00:00:00"));          // 查无此线
        rows.add(carrier(11L, 1, "DEAD", "2020-01-01 00:00:00"));          // 已撤下
        rows.add(carrier(12L, 2, "R", "2024-01-01 00:00:00"));             // 旧版已交班，不在时段
        rows.add(carrier(13L, 2, "R", "2023-05-05 00:00:00"));             // 起用日任何版本都合不拢
        rows.add(carrier(14L, 1, null, null));                             // 钉档残缺
        rows.add(carrier(15L, 1, "GOOD", "2025-01-01 00:00:00"));          // 好件
        when(carrierMapper.selectList(any(Wrapper.class))).thenReturn(rows);

        List<StaleWarnStamp> stale =
                carrierService.listStaleWarnStamp(t("2025-06-01 08:00:00"));
        assertEquals(5, stale.size());
        assertEquals(StaleWarnStamp.NOT_FOUND, byId(stale, 10L).getReason());
        assertEquals(StaleWarnStamp.REVOKED, byId(stale, 11L).getReason());
        assertEquals(StaleWarnStamp.OUT_OF_WINDOW, byId(stale, 12L).getReason());
        assertEquals(StaleWarnStamp.EFF_DATE_MISMATCH, byId(stale, 13L).getReason());
        assertEquals(StaleWarnStamp.STAMP_INCOMPLETE, byId(stale, 14L).getReason());
        assertTrue(stale.stream().noneMatch(s -> Long.valueOf(15L).equals(s.getCarrierId())));
        // 挑单不偷偷重判：缘由说法都在
        assertTrue(byId(stale, 11L).getReasonText().contains("已撤下"));
    }

    private static StaleWarnStamp byId(List<StaleWarnStamp> list, long id) {
        for (StaleWarnStamp s : list) {
            if (s.getCarrierId() == id) {
                return s;
            }
        }
        return null;
    }
}
