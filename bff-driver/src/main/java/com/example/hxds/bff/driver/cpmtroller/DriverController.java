package com.example.hxds.bff.driver.cpmtroller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.bff.driver.service.DriverService;
import com.example.hxds.common.util.R;
import com.example.hxds.dr.controller.form.RegisterNewDriverForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-06 16:34
 **/
@RestController
@RequestMapping("/driver")
@Tag(name = "DriverController", description = "司机模块Web接口")
public class DriverController {

    @Resource
    private DriverService driverService;

    @PostMapping("/registerNewDriver")
    @Operation(summary = "新司机注册")
    public R registerNewDriver(@RequestBody @Valid RegisterNewDriverForm form) {
        long driverId = driverService.registerNewDriver(form);
        //SaToken登陆验证
        StpUtil.login(driverId);
        String token = StpUtil.getTokenInfo().getTokenValue();
        return R.ok().put("token", token);
    }

}
