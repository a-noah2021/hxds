package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.ClearNewOrderQueueForm;
import com.example.hxds.bff.driver.controller.form.ReceiveNewOrderMessageForm;

import java.util.List;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 19:08
 **/
public interface NewOrderMessageService {

    void clearNewOrderQueue(ClearNewOrderQueueForm form);

    List receiveNewOrderMessage(ReceiveNewOrderMessageForm form);
}
