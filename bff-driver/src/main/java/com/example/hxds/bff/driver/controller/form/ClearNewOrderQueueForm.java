package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-05 19:02
 **/
@Data
@Schema(description = "清空新订单消息队列的表单")
public class ClearNewOrderQueueForm {

    @Schema(description = "用户ID")
    private Long userId;
}
