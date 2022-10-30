package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-30 20:04
 **/
@Data
@Schema(description = "查询司机基本信息的表单")
public class SearchDriverBaseInfoForm {
    @Schema(description = "司机ID")
    private Long driverId;
}
