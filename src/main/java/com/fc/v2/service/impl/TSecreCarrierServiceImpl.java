package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.mapper.auto.TSecreWarnLineMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.model.auto.TSecreWarnLine;
import com.fc.v2.model.custom.StaleWarnStamp;
import com.fc.v2.model.custom.WarnLevelResult;
import com.fc.v2.service.ITSecreCarrierService;
import com.fc.v2.service.ITSecreWarnLineService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 涉密载体册面Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TSecreCarrierServiceImpl extends ServiceImpl<TSecreCarrierMapper, TSecreCarrier> implements ITSecreCarrierService {

    @Autowired
    private TSecreOrgBookMapper secreOrgBookMapper;

    @Autowired
    private TSecreWarnLineMapper secreWarnLineMapper;

    @Autowired
    private ITSecreWarnLineService secreWarnLineService;

    @Override
    public TSecreCarrier selectTSecreCarrierById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper) {
        QueryWrapper<TSecreCarrier> wrapper = new QueryWrapper<TSecreCarrier>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTSecreCarrier(TSecreCarrier record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getCarrierNo());
        TSecreOrgBook refArch = secreOrgBookMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                .eq("id", record.getBookId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setBookNo(refArch.getBookNo());
        if (StringUtils.isNotEmpty(record.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getNeedNum() == null ? 0 : record.getNeedNum();
        int remOut = record.getGotNum() == null ? 0 : record.getGotNum();
        record.setLackNum(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTSecreCarrier(TSecreCarrier record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getCarrierNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TSecreCarrier>()
                    .eq("carrier_no", record.getCarrierNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TSecreCarrier>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTSecreCarrierByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTSecreCarrierById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    /**
     * 挂提醒：发问那一刻 at、离届日数 daysToDue（量的是离届那一头），请唯一口径那一处回话；
     * 层级落行的同时把线代号与起用那一日钉牢，不提醒则三格一并清空。
     * 不许先钉层数再回头配线——三格同出于 resolveWarn 那一处。
     */
    @Override
    public WarnLevelResult applyWarn(Long carrierId, BigDecimal daysToDue, Date at) {
        if (carrierId == null || daysToDue == null || at == null) {
            return WarnLevelResult.NONE;
        }
        TSecreCarrier carrier = selectTSecreCarrierById(carrierId);
        if (carrier == null) {
            return WarnLevelResult.NONE;
        }
        WarnLevelResult result = secreWarnLineService.resolveWarn(daysToDue, at);

        // 三格一律走 UpdateWrapper 显式 set：MP 实体更新默认跳过 null，
        // 不提醒时那三格的 NULL 必须真真切切落库清空，不能只在对象上置空。
        UpdateWrapper<TSecreCarrier> uw = new UpdateWrapper<TSecreCarrier>()
                .eq("id", carrierId)
                .eq("del_flag", 0)
                .set("update_time", new Date());
        if (result.isRemind()) {
            uw.set("warn_level", result.getLevel())
                    .set("warn_rule_code", result.getRuleCode())
                    .set("warn_eff_date", result.getEffDate());
        } else {
            // 不提醒：回话落这三个字，线代号与起用日两格一并留空
            uw.set("warn_level", null)
                    .set("warn_rule_code", null)
                    .set("warn_eff_date", null);
        }
        this.baseMapper.update(null, uw);
        return result;
    }

    /**
     * 钉档对不拢挑单。只核「线格在不在、对得拢对不拢」，不在此处重新归层——
     * 重新归层是另一发问时刻的事，不能拿此刻最新那条悄悄改写旧钉档。
     */
    @Override
    public List<StaleWarnStamp> listStaleWarnStamp(Date at) {
        List<StaleWarnStamp> stale = new java.util.ArrayList<StaleWarnStamp>();
        if (at == null) {
            return stale;
        }
        List<TSecreCarrier> carriers = this.baseMapper.selectList(new QueryWrapper<TSecreCarrier>()
                .eq("del_flag", 0)
                .isNotNull("warn_level"));
        // 发问那一刻的在位名单直接取自唯一口径那一回计算，挑单不许另捋一把尺子
        java.util.Map<String, TSecreWarnLine> activeByCode =
                new java.util.HashMap<String, TSecreWarnLine>();
        for (TSecreWarnLine r : secreWarnLineService.listAvailable(at)) {
            if (r.getRuleCode() != null) {
                activeByCode.put(r.getRuleCode(), r);
            }
        }
        for (TSecreCarrier c : carriers) {
            if (StringUtils.isEmpty(c.getWarnRuleCode()) || c.getWarnEffDate() == null) {
                stale.add(new StaleWarnStamp(c.getId(), c.getCarrierNo(), c.getWarnLevel(),
                        c.getWarnRuleCode(), c.getWarnEffDate(), StaleWarnStamp.STAMP_INCOMPLETE));
                continue;
            }
            List<TSecreWarnLine> sameCode = secreWarnLineMapper.selectList(new QueryWrapper<TSecreWarnLine>()
                    .eq("del_flag", 0)
                    .eq("rule_code", c.getWarnRuleCode()));
            if (sameCode.isEmpty()) {
                stale.add(new StaleWarnStamp(c.getId(), c.getCarrierNo(), c.getWarnLevel(),
                        c.getWarnRuleCode(), c.getWarnEffDate(), StaleWarnStamp.NOT_FOUND));
                continue;
            }
            TSecreWarnLine stampedVersion = null;
            for (TSecreWarnLine r : sameCode) {
                if (r.getEffStart() != null && ts(r.getEffStart()).equals(ts(c.getWarnEffDate()))) {
                    stampedVersion = r;
                }
            }
            if (stampedVersion == null) {
                // 钉着的起用那一日在同代号各版本里都合不拢：挪了旁边那条或拿去年那季的线来配
                stale.add(new StaleWarnStamp(c.getId(), c.getCarrierNo(), c.getWarnLevel(),
                        c.getWarnRuleCode(), c.getWarnEffDate(), StaleWarnStamp.EFF_DATE_MISMATCH));
                continue;
            }
            if (stampedVersion.getStatus() != null && stampedVersion.getStatus() != 0) {
                // 撤过的那一条：查得着也数得上档案，可它不配用来定层
                stale.add(new StaleWarnStamp(c.getId(), c.getCarrierNo(), c.getWarnLevel(),
                        c.getWarnRuleCode(), c.getWarnEffDate(), StaleWarnStamp.REVOKED));
                continue;
            }
            TSecreWarnLine liveVersion = activeByCode.get(c.getWarnRuleCode());
            if (liveVersion == null
                    || !ts(liveVersion.getEffStart()).equals(ts(stampedVersion.getEffStart()))) {
                // 该代号发问当下在位的版本与钉档版本不是同一版（时段已交出去/还没起用）：张冠李戴
                stale.add(new StaleWarnStamp(c.getId(), c.getCarrierNo(), c.getWarnLevel(),
                        c.getWarnRuleCode(), c.getWarnEffDate(), StaleWarnStamp.OUT_OF_WINDOW));
            }
        }
        return stale;
    }

    /** 墙钟字符串（yyyy-MM-dd HH:mm:ss），避开 JDBC serverTimezone 整体偏移 */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
