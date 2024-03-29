package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.*;
import com.example.hxds.common.util.R;
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

    @PostMapping("/driver/updateDriverAuth")
    R updateDriverAuth(UpdateDriverAuthForm form);

    @PostMapping("/driver/createDriverFaceModel")
    R createDriverFaceModel(CreateDriverFaceModelForm form);

    @PostMapping("/driver/login")
    R login(LoginForm form);

    @PostMapping("/driver/searchDriverBaseInfo")
    R searchDriverBaseInfo(SearchDriverBaseInfoForm form);

    @PostMapping("/settings/searchDriverSettings")
    R searchDriverSettings(SearchDriverSettingsForm form);

    @PostMapping("/driver/searchDriverAuth")
    R searchDriverAuth(SearchDriverAuthForm form);


}
