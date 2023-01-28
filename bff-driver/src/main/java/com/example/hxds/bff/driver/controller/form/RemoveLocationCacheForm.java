package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:43
 **/
@Data
@Schema(description = "删除司机定位缓存的表单")
public class RemoveLocationCacheForm {

    @Schema(description = "司机ID")
    private Long driverId;
}
