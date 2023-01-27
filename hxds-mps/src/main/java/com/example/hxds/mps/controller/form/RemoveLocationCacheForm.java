package com.example.hxds.mps.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:35
 **/
@Data
@Schema(description = "删除司机定位缓存的表单")
public class RemoveLocationCacheForm {
    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;
}
