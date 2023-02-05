package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 23:08
 **/
@Data
@Schema(description = "接收新订单消息的表单")
public class ReceiveNewOrderMessageForm {
    @Schema(description = "用户ID")
    private Long userId;
}
