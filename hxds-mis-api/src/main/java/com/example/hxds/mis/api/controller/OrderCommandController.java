package com.example.hxds.mis.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.AcceptCommentAppealForm;
import com.example.hxds.mis.api.controller.form.HandleCommentAppealForm;
import com.example.hxds.mis.api.controller.form.SearchAppealContentForm;
import com.example.hxds.mis.api.service.OrderCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:33
 **/
@RestController
@RequestMapping("/comment")
@Tag(name = "OrderCommentController", description = "订单评价模块Web接口")
public class OrderCommandController {

    @Resource
    private OrderCommentService orderCommentService;

    @PostMapping("/acceptCommentAppeal")
    @Operation(summary = "受理评价申诉")
    public R acceptCommentAppeal(@RequestBody @Valid AcceptCommentAppealForm form){
        int userId = StpUtil.getLoginIdAsInt();
        form.setUserId(userId);
        orderCommentService.acceptCommentAppeal(form);
        return R.ok();
    }

    @PostMapping("/handleCommentAppeal")
    @Operation(summary = "处理评价申诉")
    public R handleCommentAppeal(@RequestBody @Valid HandleCommentAppealForm form){
        int userId = StpUtil.getLoginIdAsInt();
        form.setUserId(userId);
        orderCommentService.handleCommentAppeal(form);
        return R.ok();
    }

    @PostMapping("/searchAppealContent")
    @SaCheckPermission(value = {"ROOT", "COMMENT:SELECT"}, mode = SaMode.OR)
    @Operation(summary = "查询审批工作流内容")
    public R searchAppealContent(@RequestBody @Valid SearchAppealContentForm form){
        HashMap map = orderCommentService.searchAppealContent(form);
        return R.ok().put("result",map);
    }

}
