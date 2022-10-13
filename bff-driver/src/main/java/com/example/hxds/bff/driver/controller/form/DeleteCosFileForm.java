package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotEmpty;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-14 00:45
 **/
@Data
@Schema(description = "删除腾讯云COS文件表单")
public class DeleteCosFileForm {
    @NotEmpty(message = "pathes不能为空")
    @Schema(description = "云文件路径数组")
    private String[] pathes;
}
