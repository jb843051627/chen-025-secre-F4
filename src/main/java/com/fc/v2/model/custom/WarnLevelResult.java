package com.fc.v2.model.custom;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TSecreWarnLine;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 届期提醒判级回话（唯一口径的取数凭据）。
 * <p>够不上三条线时 level 为 null、回话落「不提醒」，
 * ruleCode 与 effDate 两格一并留空，不许抄近旁那条线的数字拼体面答复。
 * 钉进载体那一行的线代号与起用那一日，必须取自这里给出的那一对。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "WarnLevelResult", description = "届期提醒判级回话")
public class WarnLevelResult {

    /** 不提醒（三条线一条也没贴着） */
    public static final WarnLevelResult NONE = new WarnLevelResult(null, null, null, null);

    @ApiModelProperty(value = "层级 1一级(最紧) 2二级 3三级；null=不提醒")
    private final Integer level;

    @ApiModelProperty(value = "作数的那条线代号；不提醒时留空")
    private final String ruleCode;

    @ApiModelProperty(value = "作数的那条线起用那一日；不提醒时留空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private final Date effDate;

    @ApiModelProperty(value = "线名（回话附注，钉档只认代号与起用日）")
    private final String ruleName;

    private WarnLevelResult(Integer level, String ruleCode, Date effDate, String ruleName) {
        this.level = level;
        this.ruleCode = ruleCode;
        this.effDate = effDate;
        this.ruleName = ruleName;
    }

    /** 贴着某条线某一档：线与层一并从这一处出，不许先钉层数再回头配线 */
    public static WarnLevelResult of(TSecreWarnLine rule, int level) {
        return new WarnLevelResult(level, rule.getRuleCode(), rule.getEffStart(), rule.getRuleName());
    }

    public boolean isRemind() {
        return level != null;
    }

    /** 回话那一栏：一级／二级／三级／不提醒，只有这四种说法 */
    public String getLevelText() {
        if (level == null) {
            return "不提醒";
        }
        if (level == 1) {
            return "一级";
        }
        if (level == 2) {
            return "二级";
        }
        return "三级";
    }

    public Integer getLevel() {
        return level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Date getEffDate() {
        return effDate;
    }

    public String getRuleName() {
        return ruleName;
    }
}
