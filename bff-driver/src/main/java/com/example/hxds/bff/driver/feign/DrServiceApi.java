package com.example.hxds.bff.driver.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.dr.controller.form.RegisterNewDriverForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-06 16:23
 **/
@FeignClient(value = "hxds-dr")
public interface DrServiceApi {

    @PostMapping("/driver/registerNewDriver")
    R registerNewDriver(RegisterNewDriverForm form);

}
