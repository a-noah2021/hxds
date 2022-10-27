package com.example.hxds.dr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-27 23:29
 **/
@Data
@Schema(description = "创建司机人脸模型归档的表单")
public class CreateDriverFaceModelForm {
    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "photo不能为空")
    @Schema(description = "司机面部照片Base64字符串")
    private String photo;
}
