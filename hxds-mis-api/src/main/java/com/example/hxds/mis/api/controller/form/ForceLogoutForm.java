package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "强制让司机下线的表单")
public class ForceLogoutForm {

    @Schema(description = "加密后的Hex数据")
    private String encryptHex;

    @Schema(description = "数字签名字符串")
    private String signStr;
}