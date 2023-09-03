package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.controller.form.SearchDriverBriefInfoForm;
import com.example.hxds.bff.customer.controller.form.SearchDriverOpenIdForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 13:28
 **/
@FeignClient(value = "hxds-dr")
public interface DrServiceApi {

    @PostMapping("/driver/searchDriverBriefInfo")
    R searchDriverBriefInfo(SearchDriverBriefInfoForm form);

    @PostMapping("/driver/searchDriverOpenId")
    R searchDriverOpenId(SearchDriverOpenIdForm form);
}
