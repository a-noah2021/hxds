package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.controller.form.DeleteUnAcceptOrderForm;
import com.example.hxds.bff.customer.controller.form.InsertOrderForm;
import com.example.hxds.bff.customer.controller.form.SearchOrderStatusForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 23:46
 **/
@FeignClient(value = "hxds-odr")
public interface OdrServiceApi {

    @PostMapping("/order/insertOrder")
    R insertOrder(InsertOrderForm form);

    @PostMapping("/order/searchOrderStatus")
    R searchOrderStatus(SearchOrderStatusForm form);

    @PostMapping("/order/deleteUnAcceptOrder")
    R deleteUnAcceptOrder(DeleteUnAcceptOrderForm form);
}
