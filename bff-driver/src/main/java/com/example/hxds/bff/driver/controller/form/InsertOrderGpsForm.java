package com.example.hxds.bff.driver.controller.form;

import com.example.hxds.bff.driver.controller.vo.InsertOrderGpsVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@Schema(description = "添加订单GPS记录的表单")
public class InsertOrderGpsForm {
    @NotEmpty(message = "list不能为空")
    @Schema(description = "GPS数据")
    private List<@Valid InsertOrderGpsVO> list;
}
