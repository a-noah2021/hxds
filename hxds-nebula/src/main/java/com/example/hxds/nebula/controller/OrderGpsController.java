package com.example.hxds.nebula.controller;

import com.example.hxds.common.util.R;
import com.example.hxds.nebula.controller.form.InsertOrderGpsForm;
import com.example.hxds.nebula.service.OrderGpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-04 21:24
 **/
@RestController
@RequestMapping("/order/gps")
@Tag(name = "OrderGpsController", description = "订单GPS记录Web接口")
public class OrderGpsController {
    @Resource
    private OrderGpsService orderGpsService;

    @PostMapping("/insertOrderGps")
    @Operation(summary = "添加订单GPS记录")
    public R insertOrderGps(@RequestBody @Valid InsertOrderGpsForm form) {
        int rows = orderGpsService.insertOrderGps(form.getList());
        return R.ok().put("rows", rows);
    }
}
