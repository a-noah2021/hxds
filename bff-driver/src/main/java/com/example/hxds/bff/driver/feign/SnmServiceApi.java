package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.ClearNewOrderQueueForm;
import com.example.hxds.bff.driver.controller.form.ReceiveNewOrderMessageForm;
import com.example.hxds.bff.driver.controller.form.SendPrivateMessageForm;
import com.example.hxds.common.util.R;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 19:03
 **/
@FeignClient(value = "hxds-snm")
public interface SnmServiceApi {

    @PostMapping("/message/order/new/clearNewOrderQueue")
    R clearNewOrderQueue(ClearNewOrderQueueForm form);

    @PostMapping("/message/order/new/receiveNewOrderMessage")
    R receiveNewOrderMessage(ReceiveNewOrderMessageForm form);

    @PostMapping("/message/sendPrivateMessage")
    @Operation(summary = "同步发送私有消息")
    R sendPrivateMessage(SendPrivateMessageForm form);

    @PostMapping("/message/sendPrivateMessageSync")
    @Operation(summary = "异步发送私有消息")
    R sendPrivateMessageSync(SendPrivateMessageForm form);
}
