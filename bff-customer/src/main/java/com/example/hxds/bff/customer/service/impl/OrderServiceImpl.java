package com.example.hxds.bff.customer.service.impl;

import com.example.hxds.bff.customer.controller.form.CreateNewOrderForm;
import com.example.hxds.bff.customer.feign.CstServiceApi;
import com.example.hxds.bff.customer.feign.MpsServiceApi;
import com.example.hxds.bff.customer.feign.RuleServiceApi;
import com.example.hxds.bff.customer.service.OrderService;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 00:13
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private MpsServiceApi mpsServiceApi;

    @Resource
    private RuleServiceApi ruleServiceApi;

    @Resource
    private CstServiceApi cstServiceApi;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Override
    public HashMap createNewOrder(CreateNewOrderForm form) {

        return null;
    }
}
