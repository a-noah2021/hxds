package com.example.hxds.snm.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 11:22
 **/
@Data
@Schema(description = "根据消息ID查询消息的表单")
public class SearchMessageByIdForm {
    @NotBlank(message = "id不能为空")
    @Schema(description = "消息的ID")
    private String id;
}

