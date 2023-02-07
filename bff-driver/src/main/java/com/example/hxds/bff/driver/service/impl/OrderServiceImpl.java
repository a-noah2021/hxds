package com.example.hxds.bff.driver.service.impl;

import cn.hutool.core.map.MapUtil;
import com.example.hxds.bff.driver.controller.form.AcceptNewOrderForm;
import com.example.hxds.bff.driver.feign.OdrServiceApi;
import com.example.hxds.bff.driver.service.OrderService;
import com.example.hxds.common.util.R;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-07 23:26
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OdrServiceApi odrServiceApi;

    @Override
    public String acceptNewOrder(AcceptNewOrderForm form) {
        R r = odrServiceApi.acceptNewOrder(form);
        String result = MapUtil.getStr(r, "result");
        return result;
    }
}
