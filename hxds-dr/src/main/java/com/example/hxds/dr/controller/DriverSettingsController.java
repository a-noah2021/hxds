package com.example.hxds.dr.controller;

import com.example.hxds.common.util.R;
import com.example.hxds.dr.controller.form.SearchDriverSettingsForm;
import com.example.hxds.dr.service.DriverSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;

@RestController
@RequestMapping("/settings")
@Tag(name = "SettingsController", description = "Driver Settings Web Controller")
public class DriverSettingsController {
    @Resource
    private DriverSettingsService driverSettingsService;

    @PostMapping("/searchDriverSettings")
    @Operation(summary = "Search Driver Settings")
    public R searchDriverSettings(@RequestBody @Valid SearchDriverSettingsForm form){
        HashMap result = driverSettingsService.searchDriverSettings(form.getDriverId());
        return R.ok().put("result", result);
    }
}
