package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Schema(description = "更新部门表单")
@Data
public class UpdateDeptForm {

    @NotNull(message = "id不能为空")
    @Schema(description = "部门ID")
    private Integer id;

    @NotBlank(message = "deptName不能为空")
    @Schema(description = "部门名称")
    private String deptName;

    @Pattern(regexp = "^1\\d{10}$|^(0\\d{2,3}\\-){0,1}[1-9]\\d{6,7}$",message = "tel内容不正确")
    @Schema(description = "电话")
    private String tel;

    @Email(message = "email内容不正确")
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "备注")
    @Length(max = 20,message = "desc不能超过20个字符")
    private String desc;

}