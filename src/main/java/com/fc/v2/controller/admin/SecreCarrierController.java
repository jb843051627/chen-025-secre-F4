package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.custom.WarnLevelResult;
import com.fc.v2.service.ITSecreCarrierService;
import com.fc.v2.service.ITSecreWarnLineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 涉密载体册面 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "涉密载体册面")
@Controller
@RequestMapping("/SecreCarrierController")
public class SecreCarrierController extends BaseController {

    private final String prefix = "admin/secreCarrier";

    @Autowired
    private ITSecreCarrierService secreCarrierService;

    @Autowired
    private ITSecreWarnLineService warnLineService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("secre:secreCarrier:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "涉密载体册面集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("secre:secreCarrier:list")
    @ResponseBody
    public ResultTable list(TSecreCarrier record) {
        QueryWrapper<TSecreCarrier> queryWrapper = new QueryWrapper<TSecreCarrier>();
        startPage();
        com.github.pagehelper.PageInfo<TSecreCarrier> page =
                new com.github.pagehelper.PageInfo<TSecreCarrier>(secreCarrierService.selectTSecreCarrierList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "涉密载体册面新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("secre:secreCarrier:add")
    @ResponseBody
    public AjaxResult add(TSecreCarrier record) {
        return toAjax(secreCarrierService.insertTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("secre:secreCarrier:edit")
    @ResponseBody
    public AjaxResult editSave(TSecreCarrier record) {
        return toAjax(secreCarrierService.updateTSecreCarrier(record));
    }

    @Log(title = "涉密载体册面删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("secre:secreCarrier:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(secreCarrierService.deleteTSecreCarrierByIds(ids));
    }

    /**
     * 通道一（只问不落行）：发问那一刻 at、离届日数 days，回层级与线代号、起用日。
     * 与落行通道同走 resolveWarn 一处，两遍必须给出同一层。
     */
    @Log(title = "届期提醒判级问话", action = "warnResolve")
    @ApiOperation(value = "届期提醒判级问话", notes = "只回话不落行")
    @GetMapping("/warn/resolve")
    @RequiresPermissions("secre:secreCarrier:view")
    @ResponseBody
    public AjaxResult warnResolve(@RequestParam("days") BigDecimal days,
                                  @RequestParam("at") String at) {
        Date askAt = parseAt(at);
        if (askAt == null) {
            return AjaxResult.error("发问时刻格式应为 yyyy-MM-dd HH:mm:ss");
        }
        return AjaxResult.successData(200, warnLineService.resolveWarn(days, askAt));
    }

    /**
     * 通道二（问完钉档）：层级连同线代号、起用那一日一并钉进载体那一行；
     * 不提醒时三格一并清空。回话与落行的那一对同出一处，合不拢就是错。
     */
    @Log(title = "届期提醒钉档", action = "warnApply")
    @ApiOperation(value = "届期提醒钉档", notes = "判级并把层级/线代号/起用日落行")
    @PostMapping("/warn/apply")
    @RequiresPermissions("secre:secreCarrier:edit")
    @ResponseBody
    public AjaxResult warnApply(@RequestParam("id") Long id,
                                @RequestParam("days") BigDecimal days,
                                @RequestParam("at") String at) {
        Date askAt = parseAt(at);
        if (askAt == null) {
            return AjaxResult.error("发问时刻格式应为 yyyy-MM-dd HH:mm:ss");
        }
        WarnLevelResult result = secreCarrierService.applyWarn(id, days, askAt);
        AjaxResult ok = AjaxResult.successData(200, result);
        ok.put("levelText", result.getLevelText());
        return ok;
    }

    /** 屏上在位名单：与把线册从头捋一遍凑的名单同源于 listAvailable 那一回计算 */
    @ApiOperation(value = "发问时刻在位线名单", notes = "与线册全捋同源")
    @GetMapping("/warn/active")
    @RequiresPermissions("secre:secreCarrier:view")
    @ResponseBody
    public AjaxResult warnActive(@RequestParam("at") String at) {
        Date askAt = parseAt(at);
        if (askAt == null) {
            return AjaxResult.error("发问时刻格式应为 yyyy-MM-dd HH:mm:ss");
        }
        return AjaxResult.successData(200, warnLineService.listAvailable(askAt));
    }

    /** 挂着线代号却请不出当下在位线的件，一单一单挑出来 */
    @ApiOperation(value = "钉档对不拢挑单", notes = "查无/撤下/不在时段/起用日合不拢/钉档残缺")
    @GetMapping("/warn/stale")
    @RequiresPermissions("secre:secreCarrier:view")
    @ResponseBody
    public AjaxResult warnStale(@RequestParam("at") String at) {
        Date askAt = parseAt(at);
        if (askAt == null) {
            return AjaxResult.error("发问时刻格式应为 yyyy-MM-dd HH:mm:ss");
        }
        return AjaxResult.successData(200, secreCarrierService.listStaleWarnStamp(askAt));
    }

    private static Date parseAt(String at) {
        if (at == null || at.trim().isEmpty()) {
            return null;
        }
        try {
            String v = at.trim();
            if (v.length() == 10) {
                return new SimpleDateFormat("yyyy-MM-dd").parse(v);
            }
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(v);
        } catch (ParseException e) {
            return null;
        }
    }
}
