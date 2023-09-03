package com.example.hxds.dr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 17:49
 **/
@Data
@Schema(description = "转账的表单")
public class TransferForm {
    @NotBlank(message = "uuid不能为空")
    @Schema(description = "uuid")
    private String uuid;

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    @NotBlank(message = "amount不能为空")
    @Pattern(regexp = "^[1-9]\\d*\\.\\d{1,2}$|^0\\.\\d{1,2}$|^[1-9]\\d*$", message = "amount内容不正确")
    @Schema(description = "转账金额")
    private String amount;

    @NotNull(message = "type不能为空")
    @Range(min = 1, max = 3, message = "type内容不正确")
    @Schema(description = "充值类型")
    private Byte type;

    @NotBlank(message = "remark不能为空")
    @Schema(description = "备注信息")
    private String remark;

}
