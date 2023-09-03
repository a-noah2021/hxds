package com.example.hxds.bff.customer.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 22:50
 **/
@Data
@Schema(description = "查询客户OpenId的表单")
public class SearchCustomerOpenIdForm {

    @Schema(description = "客户ID")
    private Long customerId;
}