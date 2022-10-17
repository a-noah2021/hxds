package com.example.hxds.dr.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.*;

@Data
@Schema(description = "更新司机认证信息表单")
public class UpdateDriverAuthForm {

    @NotNull(message = "driverId不能为空")
    @Min(value = 1, message = "driverId不能小于1")
    @Schema(description = "司机ID")
    private Long driverId;

    //@NotBlank比@NotNull多了一层对空字符串的校验
    @NotBlank(message = "name不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "sex不能为空")
    @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
    @Schema(description = "性别")
    private String sex;

    @NotBlank(message = "pid不能为空")
    @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
    @Schema(description = "身份证")
    private String pid;

    @NotBlank(message = "birthday不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "birthday内容不正确")
    @Schema(description = "生日")
    private String birthday;

    @NotBlank(message = "tel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
    @Schema(description = "电话")
    private String tel;

    @NotBlank(message = "email不能为空")
    @Email(message = "email内容不正确")
    @Schema(description = "电子信箱")
    private String email;

    @NotBlank(message = "mailAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "mailAddress内容不正确")
    @Schema(description = "收信地址")
    private String mailAddress;

    @NotBlank(message = "contactName不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "contactName内容不正确")
    @Schema(description = "应急联络人")
    private String contactName;

    @NotBlank(message = "contactTel不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "contactTel内容不正确")
    @Schema(description = "应急联络人电话")
    private String contactTel;

    @NotBlank(message = "idcardAddress不能为空")
    @Pattern(regexp = "^[0-9a-zA-Z\\u4e00-\\u9fa5\\-]{6,50}$", message = "idcardAddress内容不正确")
    @Schema(description = "身份证地址")
    private String idcardAddress;

    @NotBlank(message = "idcardFront不能为空")
    @Schema(description = "身份证正面")
    private String idcardFront;

    @NotBlank(message = "idcardBack不能为空")
    @Schema(description = "身份证背面")
    private String idcardBack;

    @NotBlank(message = "idcardHolding不能为空")
    @Schema(description = "手持身份证")
    private String idcardHolding;

    @NotBlank(message = "idcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "idcardExpiration内容不正确")
    @Schema(description = "身份证有效期")
    private String idcardExpiration;

    @NotBlank(message = "drcardType不能为空")
    @Pattern(regexp = "^(A[1-3]|B[12]|C[1-4]|[D-FMNP])$", message = "drcardType内容不正确")
    @Schema(description = "驾驶证类别")
    private String drcardType;

    @NotBlank(message = "drcardExpiration不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardExpiration内容不正确")
    @Schema(description = "驾驶证有效期")
    private String drcardExpiration;

    @NotBlank(message = "drcardIssueDate不能为空")
    @Pattern(regexp = "^((((1[6-9]|[2-9]\\d)\\d{2})-(0?[13578]|1[02])-(0?[1-9]|[12]\\d|3[01]))|(((1[6-9]|[2-9]\\d)\\d{2})-(0?[13456789]|1[012])-(0?[1-9]|[12]\\d|30))|(((1[6-9]|[2-9]\\d)\\d{2})-0?2-(0?[1-9]|1\\d|2[0-8]))|(((1[6-9]|[2-9]\\d)(0[48]|[2468][048]|[13579][26])|((16|[2468][048]|[3579][26])00))-0?2-29-))$", message = "drcardIssueDate内容不正确")
    @Schema(description = "驾驶证初次领证日期")
    private String drcardIssueDate;

    @NotBlank(message = "drcardFront不能为空")
    @Schema(description = "驾驶证正面")
    private String drcardFront;

    @NotBlank(message = "drcardBack不能为空")
    @Schema(description = "驾驶证背面")
    private String drcardBack;

    @NotBlank(message = "drcardHolding不能为空")
    @Schema(description = "手持驾驶证")
    private String drcardHolding;

}