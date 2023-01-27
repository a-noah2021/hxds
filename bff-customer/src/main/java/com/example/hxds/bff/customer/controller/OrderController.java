package com.example.hxds.bff.customer.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.bff.customer.controller.form.CreateNewOrderForm;
import com.example.hxds.bff.customer.service.OrderService;
import com.example.hxds.common.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;

import static com.example.hxds.common.constants.HxdsConstants.RESULT_MAP_KEY;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 23:35
 **/
@RestController
@RequestMapping("/order")
@Tag(name = "OrderController", description = "订单模块Web接口")
public class OrderController {
    @Resource
    private OrderService orderService;

    @PostMapping("/createNewOrder")
    @Operation(summary = "创建新订单")
    @SaCheckLogin
    public R createNewOrder(@RequestBody @Valid CreateNewOrderForm form) {
        long customerId = StpUtil.getLoginIdAsLong();
        form.setCustomerId(customerId);
        HashMap result = orderService.createNewOrder(form);
        return R.ok().put(RESULT_MAP_KEY, result);
    }
}
