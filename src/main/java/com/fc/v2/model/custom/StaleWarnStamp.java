package com.fc.v2.model.custom;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 钉档对不拢挑单：屏上挂着线代号、翻回册里那一刻却请不出在位线的件，一单一单落在这里。
 * <p>撤过的那一条另算——册里查得着、现行那排里也数得上它，可它不配用来定层，
 * 照样进这张单子；不许缀在最新那句后头蒙混。
 *
 * @author fuce
 * @date 2026-10-05
 */
@ApiModel(value = "StaleWarnStamp", description = "钉档对不拢挑单")
public class StaleWarnStamp {

    /** 册里按代号根本查不着这条线（可能被删档/代号抄错） */
    public static final String NOT_FOUND = "NOT_FOUND";
    /** 线在册里，但已撤下（status=1），不配用来定层 */
    public static final String REVOKED = "REVOKED";
    /** 线没撤，可发问这一刻不在它的起用—交班时段里（张冠李戴） */
    public static final String OUT_OF_WINDOW = "OUT_OF_WINDOW";
    /** 钉着的起用那一日与册里这条线的起用日对不上（挪了旁边那条/拿去年那季的线凑） */
    public static final String EFF_DATE_MISMATCH = "EFF_DATE_MISMATCH";
    /** 层级一格在、线代号与起用日两格却空着，回话来路不明 */
    public static final String STAMP_INCOMPLETE = "STAMP_INCOMPLETE";

    @ApiModelProperty(value = "载体主键")
    private final Long carrierId;

    @ApiModelProperty(value = "载体件码")
    private final String carrierNo;

    @ApiModelProperty(value = "屏上挂着的提醒层级")
    private final Integer stampedLevel;

    @ApiModelProperty(value = "钉档的线代号")
    private final String stampedRuleCode;

    @ApiModelProperty(value = "钉档的起用那一日")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private final Date stampedEffDate;

    @ApiModelProperty(value = "挑单缘由码")
    private final String reason;

    @ApiModelProperty(value = "挑单缘由（说法）")
    private final String reasonText;

    public StaleWarnStamp(Long carrierId, String carrierNo, Integer stampedLevel,
                          String stampedRuleCode, Date stampedEffDate, String reason) {
        this.carrierId = carrierId;
        this.carrierNo = carrierNo;
        this.stampedLevel = stampedLevel;
        this.stampedRuleCode = stampedRuleCode;
        this.stampedEffDate = stampedEffDate;
        this.reason = reason;
        this.reasonText = reasonText(reason);
    }

    private static String reasonText(String reason) {
        if (NOT_FOUND.equals(reason)) {
            return "线代号在册里查不着，当下请不出在位线";
        }
        if (REVOKED.equals(reason)) {
            return "该线已撤下，不配用来定层，也不许缀在最新那句后头";
        }
        if (OUT_OF_WINDOW.equals(reason)) {
            return "该线发问这一刻不在起用—交班时段内，属张冠李戴";
        }
        if (EFF_DATE_MISMATCH.equals(reason)) {
            return "钉档的起用那一日与册里这条线合不拢";
        }
        if (STAMP_INCOMPLETE.equals(reason)) {
            return "层级在、线代号与起用日两格却空着";
        }
        return reason;
    }

    public Long getCarrierId() {
        return carrierId;
    }

    public String getCarrierNo() {
        return carrierNo;
    }

    public Integer getStampedLevel() {
        return stampedLevel;
    }

    public String getStampedRuleCode() {
        return stampedRuleCode;
    }

    public Date getStampedEffDate() {
        return stampedEffDate;
    }

    public String getReason() {
        return reason;
    }

    public String getReasonText() {
        return reasonText;
    }
}
