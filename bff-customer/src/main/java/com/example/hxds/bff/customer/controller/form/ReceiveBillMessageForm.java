package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveBillMessageForm {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户身份")
    private String identity;
}
