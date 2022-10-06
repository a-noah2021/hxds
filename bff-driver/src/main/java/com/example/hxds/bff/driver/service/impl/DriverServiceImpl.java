package com.example.hxds.bff.driver.service.impl;

import cn.hutool.core.convert.Convert;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.bff.driver.feign.DrServiceApi;
import com.example.hxds.bff.driver.service.DriverService;
import com.example.hxds.common.util.R;
import com.example.hxds.dr.controller.form.RegisterNewDriverForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-06 16:28
 **/
@Service
public class DriverServiceImpl implements DriverService {

    @Resource
    private DrServiceApi drServiceApi;

    @Override
    @Transactional
    @LcnTransaction
    public long registerNewDriver(RegisterNewDriverForm form) {
        R r = drServiceApi.registerNewDriver(form);
        long userId = Convert.toLong(r.get("userId"));
        return userId;
    }
}
