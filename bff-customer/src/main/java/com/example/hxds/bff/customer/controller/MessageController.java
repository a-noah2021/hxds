package com.example.hxds.bff.customer.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.bff.customer.controller.form.ReceiveBillMessageForm;
import com.example.hxds.bff.customer.service.MessageService;
import com.example.hxds.common.util.R;
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
 * @date: 2023-09-02 00:05
 **/
@RestController
@RequestMapping("/message")
@Tag(name = "MessageController", description = "消息模块Web接口")
public class MessageController {
    @Resource
    private MessageService messageService;

    @PostMapping("/receiveBillMessage")
    @SaCheckLogin
    @Operation(summary = "同步接收新订单消息")
    public R receiveBillMessage(@RequestBody @Valid ReceiveBillMessageForm form){
        long customerId = StpUtil.getLoginIdAsLong();
        form.setUserId(customerId);
        form.setIdentity("customer_bill");
        String msg = messageService.receiveBillMessage(form);
        return R.ok().put("result",msg);
    }
}
