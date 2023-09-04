package com.example.hxds.mis.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchCommentByPageForm;
import com.example.hxds.mis.api.service.OrderCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
/**
 * @author zhanglumin
 * @since 2023/9/4 4:40 PM
 */
@RestController
@RequestMapping("/comment")
@Tag(name = "OrderCommentController", description = "订单评价模块Web接口")
public class OrderCommentController {

    @Resource
    private OrderCommentService orderCommentService;

    @PostMapping("/searchCommentByPage")
    @SaCheckPermission(value = {"ROOT", "COMMENT:SELECT"}, mode = SaMode.OR)
    @Operation(summary = "查询订单评价分页记录")
    public R searchCommentByPage(@RequestBody @Valid SearchCommentByPageForm form) {
        int userId = StpUtil.getLoginIdAsInt();
        form.setUserId(userId);
        PageUtils pageUtils = orderCommentService.searchCommentByPage(form);
        return R.ok().put("result", pageUtils);
    }

}