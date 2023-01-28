package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:48
 **/
@Data
@Schema(description = "更新司机GPS坐标缓存的表单")
public class UpdateLocationCacheForm {

    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "latitude不能为空")
    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "latitude内容不正确")
    @Schema(description = "纬度")
    private String latitude;

    @NotBlank(message = "longitude不能为空")
    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "longitude内容不正确")
    @Schema(description = "经度")
    private String longitude;

    @NotNull(message = "rangeDistance不能为空")
    @Range(min = 1, max = 5, message = "rangeDistance范围错误")
    @Schema(description = "接收几公里内的订单")
    private Integer rangeDistance;

    @NotNull(message = "orderDistance不能为空")
    @Schema(description = "接收代驾里程几公里以上的订单")
    @Range(min = 0, max = 30, message = "orderDistance范围错误")
    private Integer orderDistance;

    @Pattern(regexp = "^(([1-9]\\d?)|(1[0-7]\\d))(\\.\\d{1,18})|180|0(\\.\\d{1,18})?$", message = "orientateLongitude内容不正确")
    @Schema(description = "定向接单的经度")
    private String orientateLongitude;

    @Pattern(regexp = "^(([1-8]\\d?)|([1-8]\\d))(\\.\\d{1,18})|90|0(\\.\\d{1,18})?$", message = "orientateLatitude内容不正确")
    @Schema(description = "定向接单的纬度")
    private String orientateLatitude;
}