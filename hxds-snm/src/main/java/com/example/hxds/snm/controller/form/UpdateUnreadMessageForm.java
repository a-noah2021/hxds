package com.example.hxds.snm.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 11:15
 **/
@Data
@Schema(description = "把未读消息更新成已读的表单")
public class UpdateUnreadMessageForm {
    @NotBlank(message = "id不能为空")
    @Schema(description = "ref消息ID")
    private String id;
}
