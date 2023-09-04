package com.example.hxds.bff.driver.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.bff.driver.controller.form.StartCommentWorkflowForm;
import com.example.hxds.bff.driver.service.OrderCommentService;
import com.example.hxds.common.util.R;
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
 * @since 2023/9/4 3:44 PM
 */
@RestController
@RequestMapping("/comment")
@Tag(name = "OrderCommentController", description = "订单评价Web接口")
public class OrderCommentController {
    @Resource
    private OrderCommentService orderCommentService;

    @PostMapping("/startCommentWorkflow")
    @Operation(summary = "开启评价申诉工作流")
    public R startCommentWorkflow(@RequestBody @Valid StartCommentWorkflowForm form){
        long driverId = StpUtil.getLoginIdAsLong();
        form.setDriverId(driverId);
        orderCommentService.startCommentWorkflow(form);
        return R.ok();
    }
}
