package com.example.hxds.bff.customer.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.hxds.bff.customer.controller.form.SearchOrderLocationCacheForm;
import com.example.hxds.bff.customer.service.OrderLocationService;
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

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 00:11
 **/
@RestController
@RequestMapping("/order/location")
@Tag(name = "OrderLocationController", description = "订单定位服务Web接口")
public class OrderLocationController {

    @Resource
    private OrderLocationService orderLocationService;

    @PostMapping("/searchOrderLocationCache")
    @Operation(summary = "查询订单定位缓存")
    @SaCheckLogin
    public R searchOrderLocationCache(@RequestBody @Valid SearchOrderLocationCacheForm form){
        HashMap map = orderLocationService.searchOrderLocationCache(form);
        return R.ok().put("result",map);
    }
}
