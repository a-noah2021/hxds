package com.example.hxds.bff.driver.service.impl;

import com.example.hxds.bff.driver.controller.form.ClearNewOrderQueueForm;
import com.example.hxds.bff.driver.controller.form.ReceiveNewOrderMessageForm;
import com.example.hxds.bff.driver.feign.SnmServiceApi;
import com.example.hxds.bff.driver.service.NewOrderMessageService;
import com.example.hxds.common.util.R;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 19:09
 **/
@Service
public class NewOrderMessageServiceImpl implements NewOrderMessageService {

    @Resource
    private SnmServiceApi snmServiceApi;

    @Override
    public void clearNewOrderQueue(ClearNewOrderQueueForm form) {
        snmServiceApi.clearNewOrderQueue(form);
    }

    @Override
    public List receiveNewOrderMessage(ReceiveNewOrderMessageForm form) {
        R r = snmServiceApi.receiveNewOrderMessage(form);
        List list = (List) r.get("result");
        return list;
    }
}
