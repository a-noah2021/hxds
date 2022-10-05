package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

@Data
@Schema(description = "添加角色表单")
public class InsertRoleForm {

    @NotBlank(message = "roleName不能为空")
    @Schema(description = "角色名称")
    private String roleName;

    @NotEmpty(message = "permissions不能为空")
    @Schema(description = "权限")
    private Integer[] permissions;


    @Length(max = 20,message = "desc不能超过20个字符")
    @Schema(description = "备注")
    private String desc;
}