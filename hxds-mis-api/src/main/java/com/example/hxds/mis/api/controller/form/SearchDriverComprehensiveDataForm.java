package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "查询司机综合数据的表单")
public class SearchDriverComprehensiveDataForm {
    @NotNull(message = "realAuth不能为空")
    @Range(min = 1, max = 3, message = "realAuth范围不正确")
    @Schema(description = "是否已经实名认证")
    private Byte realAuth;

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;
}


