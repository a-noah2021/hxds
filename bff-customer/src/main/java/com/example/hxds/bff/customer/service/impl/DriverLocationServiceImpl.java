package com.example.hxds.bff.customer.service.impl;

import com.example.hxds.bff.customer.controller.form.RemoveLocationCacheForm;
import com.example.hxds.bff.customer.controller.form.UpdateLocationCacheForm;
import com.example.hxds.bff.customer.feign.MpsServiceApi;
import com.example.hxds.bff.customer.service.DriverLocationService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:53
 **/
@Service
public class DriverLocationServiceImpl implements DriverLocationService {
    @Resource
    private MpsServiceApi mpsServiceApi;

    @Override
    public void updateLocationCache(UpdateLocationCacheForm form) {
        mpsServiceApi.updateLocationCache(form);
    }

    @Override
    public void removeLocationCache(RemoveLocationCacheForm form) {
        mpsServiceApi.removeLocationCache(form);
    }
}
