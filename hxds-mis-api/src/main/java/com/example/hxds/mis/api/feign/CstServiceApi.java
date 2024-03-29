package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchCustomerBriefInfoForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-07 22:53
 **/
@FeignClient(value = "hxds-cst")
public interface CstServiceApi {
    @PostMapping("/customer/searchCustomerBriefInfo")
    R searchCustomerBriefInfo(SearchCustomerBriefInfoForm form);
}
