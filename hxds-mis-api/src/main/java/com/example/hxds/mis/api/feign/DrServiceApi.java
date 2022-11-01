package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchDriverByPageForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-01 22:45
 **/
@FeignClient(value = "hxds-dr")
public interface DrServiceApi {

    @PostMapping("/driver/searchDriverByPage")
    R searchDriverByPage(SearchDriverByPageForm form);


}
