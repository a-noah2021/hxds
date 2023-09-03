package com.example.hxds.cst.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.R;
import com.example.hxds.cst.controller.form.*;
import com.example.hxds.cst.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/customer")
@Tag(name = "CustomerController", description = "客户Web接口")
public class CustomerController {
    @Resource
    private CustomerService customerService;

    @PostMapping("/registerNewCustomer")
    @Operation(summary = "注册新司机")
    public R registerNewCustomer(@RequestBody @Valid RegisterNewCustomerForm form) {
        Map param = BeanUtil.beanToMap(form);
        String userId = customerService.registerNewCustomer(param);
        return R.ok().put("userId", userId);
    }

    @PostMapping("/login")
    @Operation(summary = "登录系统")
    public R login(@RequestBody @Valid LoginForm form){
        String userId = customerService.login(form.getCode());
        return R.ok().put("userId", userId);
    }

    @PostMapping("/searchCustomerInfoInOrder")
    @Operation(summary = "查询订单中的客户信息")
    public R searchCustomerInfoInOrder(@RequestBody @Valid SearchCustomerInfoInOrderForm form) {
        HashMap map = customerService.searchCustomerInfoInOrder(form.getCustomerId());
        return R.ok().put("result", map);
    }

    @PostMapping("/searchCustomerBriefInfo")
    @Operation(summary = "查询客户简明信息")
    public R searchCustomerBriefInfo(@RequestBody @Valid SearchCustomerBriefInfoForm form) {
        HashMap map = customerService.searchCustomerBriefInfo(form.getCustomerId());
        return R.ok().put("result", map);
    }

    @PostMapping("/searchCustomerOpenId")
    @Operation(summary = "查询客户的OpenId")
    public R searchCustomerOpenId(@RequestBody @Valid SearchCustomerOpenIdForm form){
        String openId = customerService.searchCustomerOpenId(form.getCustomerId());
        return R.ok().put("result",openId);
    }
}

