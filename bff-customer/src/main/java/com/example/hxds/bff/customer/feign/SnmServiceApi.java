package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.controller.form.ReceiveBillMessageForm;
import com.example.hxds.bff.customer.controller.form.SendNewOrderMessageForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 15:08
 **/
@FeignClient(value = "hxds-snm")
public interface SnmServiceApi {

    @PostMapping("/message/order/new/sendNewOrderMessageAsync")
    R sendNewOrderMessageAsync(SendNewOrderMessageForm form);

    @PostMapping("/message/receiveBillMessage")
    R receiveBillMessage(ReceiveBillMessageForm form);
}
