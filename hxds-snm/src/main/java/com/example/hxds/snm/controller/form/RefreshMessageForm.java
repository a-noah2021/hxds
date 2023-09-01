package com.example.hxds.snm.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 11:28
 **/
@Data
@Schema(description = "刷新用户消息的表单")
public class RefreshMessageForm {

    @NotNull(message = "userId不能为空")
    @Min(value = 1, message = "userId不能小于1")
    @Schema(description = "用户ID")
    private Long userId;

    @NotBlank(message = "identity不能为空")
    @Pattern(regexp = "^driver$|^mis$|^customer$",message = "identity内容不正确")
    @Schema(description = "用户身份")
    private String identity;

}