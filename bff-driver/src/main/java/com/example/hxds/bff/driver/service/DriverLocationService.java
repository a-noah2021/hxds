package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.RemoveLocationCacheForm;
import com.example.hxds.bff.driver.controller.form.UpdateLocationCacheForm;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:52
 **/
public interface DriverLocationService {

    void updateLocationCache(UpdateLocationCacheForm form);

    void removeLocationCache(RemoveLocationCacheForm form);

}
