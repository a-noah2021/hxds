package com.example.hxds.dr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-30 18:06
 **/
@Data
@Schema(description = "司机登陆表单")
public class LoginForm {

    @NotBlank(message = "code不能为空")
    @Schema(description = "微信小程序临时授权")
    private String code;
/*  TODO: 司机端登陆抛异常
    @NotBlank(message = "phoneCode不能为空")
    @Schema(description = "微信小程序获取电话号码临时授权")
    private String phoneCode;*/
}
