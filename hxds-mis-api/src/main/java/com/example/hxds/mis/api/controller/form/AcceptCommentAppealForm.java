package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:27
 **/
@Data
@Schema(description = "受理订单评价申诉的表单")
public class AcceptCommentAppealForm {

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "用户姓名")
    private String userName;

    @NotNull(message = "commentId不能为空")
    @Min(value = 1, message = "commentId不能小于1")
    @Schema(description = "评价ID")
    private Long commentId;
}
