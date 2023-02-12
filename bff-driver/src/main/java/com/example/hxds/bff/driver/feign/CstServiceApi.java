package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.SearchCustomerInfoInOrderForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-12 19:31
 **/
@FeignClient(value = "hxds-cst")
public interface CstServiceApi {

    @PostMapping("/customer/searchCustomerInfoInOrder")
    R searchCustomerInfoInOrder(SearchCustomerInfoInOrderForm form);

}
