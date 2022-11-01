package com.example.hxds.mis.api.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
@Schema(description = "查询司机分页记录的表单")
public class SearchDriverByPageForm {

    @NotNull(message = "page不能为空")
    @Min(value = 1, message = "page不能小于1")
    @Schema(description = "页数")
    private Integer page;

    @NotNull(message = "length不能为空")
    @Range(min = 10, max = 50, message = "length必须在10~50之间")
    @Schema(description = "每页记录数")
    private Integer length;

    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "name内容不正确")
    @Schema(description = "姓名")
    private String name;

    @Pattern(regexp = "^1\\d{10}$", message = "tel内容不正确")
    @Schema(description = "电话")
    private String tel;

    @Pattern(regexp = "^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$", message = "pid内容不正确")
    @Schema(description = "身份证")
    private String pid;

    @Pattern(regexp = "^男$|^女$", message = "sex内容不正确")
    @Schema(description = "性别")
    private String sex;

    @Range(min = 1, max = 3, message = "realAuth范围不对")
    @Schema(description = "实名认证")
    private Byte realAuth;

    @Range(min = 1, max = 3, message = "status范围不对")
    @Schema(description = "状态")
    private Byte status;
}
