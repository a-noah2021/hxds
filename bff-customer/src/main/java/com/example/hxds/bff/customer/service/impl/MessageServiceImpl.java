package com.example.hxds.bff.customer.service.impl;

import cn.hutool.core.map.MapUtil;
import com.example.hxds.bff.customer.controller.form.ReceiveBillMessageForm;
import com.example.hxds.bff.customer.feign.SnmServiceApi;
import com.example.hxds.bff.customer.service.MessageService;
import com.example.hxds.common.util.R;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 00:03
 **/
public class MessageServiceImpl implements MessageService {

    @Resource
    private SnmServiceApi snmServiceApi;

    @Override
    public String receiveBillMessage(ReceiveBillMessageForm form) {
        R r = snmServiceApi.receiveBillMessage(form);
        String map = MapUtil.getStr(r, "result");
        return map;
    }
}
