package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 涉密载体册面对象 t_secre_carrier
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_secre_carrier")
@ApiModel(value = "TSecreCarrier", description = "涉密载体册面")
public class TSecreCarrier implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 涉密载体件码 */
    @TableField("carrier_no")
    @ApiModelProperty(value = "涉密载体件码")
    private String carrierNo;

    /** 挂在哪一册名录名下 */
    @TableField("book_id")
    @ApiModelProperty(value = "挂在哪一册名录名下")
    private Integer bookId;

    /** 名录代号（冗余自名录） */
    @TableField("book_no")
    @ApiModelProperty(value = "名录代号（冗余自名录）")
    private String bookNo;

    /** 使用岗或承办岗代号 */
    @TableField("post_no")
    @ApiModelProperty(value = "使用岗或承办岗代号")
    private String postNo;

    /** 应交件数 */
    @TableField("need_num")
    @ApiModelProperty(value = "应交件数")
    private Integer needNum;

    /** 已收件数 */
    @TableField("got_num")
    @ApiModelProperty(value = "已收件数")
    private Integer gotNum;

    /** 还差几件（轧出来，不手填） */
    @TableField("lack_num")
    @ApiModelProperty(value = "还差几件（轧出来，不手填）")
    private Integer lackNum;

    /** 随件交来的载体题名与要件 */
    @TableField("content")
    @ApiModelProperty(value = "随件交来的载体题名与要件")
    private String content;

    /** 册面情形 0新入册 1已收齐 2缺项 */
    @TableField("status")
    @ApiModelProperty(value = "册面情形 0新入册 1已收齐 2缺项")
    private Integer status;

    /** 届期提醒层级 1一级(掐得最紧) 2二级 3三级；够不上三条线为 NULL（不提醒） */
    @TableField("warn_level")
    @ApiModelProperty(value = "届期提醒层级 1一级(掐得最紧) 2二级 3三级；够不上三条线为 NULL（不提醒）")
    private Integer warnLevel;

    /** 判级当口作数的那条线代号（钉档，留得追） */
    @TableField("warn_rule_code")
    @ApiModelProperty(value = "判级当口作数的那条线代号（钉档，留得追）")
    private String warnRuleCode;

    /** 那条线的起用那一日（钉档，留得追） */
    @TableField("warn_eff_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "那条线的起用那一日（钉档，留得追）")
    private java.util.Date warnEffDate;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCarrierNo() {
        return carrierNo;
    }

    public void setCarrierNo(String carrierNo) {
        this.carrierNo = carrierNo;
    }

    public Integer getBookId() {
        return bookId;
    }

    public void setBookId(Integer bookId) {
        this.bookId = bookId;
    }

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public String getPostNo() {
        return postNo;
    }

    public void setPostNo(String postNo) {
        this.postNo = postNo;
    }

    public Integer getNeedNum() {
        return needNum;
    }

    public void setNeedNum(Integer needNum) {
        this.needNum = needNum;
    }

    public Integer getGotNum() {
        return gotNum;
    }

    public void setGotNum(Integer gotNum) {
        this.gotNum = gotNum;
    }

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getWarnLevel() {
        return warnLevel;
    }

    public void setWarnLevel(Integer warnLevel) {
        this.warnLevel = warnLevel;
    }

    public String getWarnRuleCode() {
        return warnRuleCode;
    }

    public void setWarnRuleCode(String warnRuleCode) {
        this.warnRuleCode = warnRuleCode;
    }

    public java.util.Date getWarnEffDate() {
        return warnEffDate;
    }

    public void setWarnEffDate(java.util.Date warnEffDate) {
        this.warnEffDate = warnEffDate;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
