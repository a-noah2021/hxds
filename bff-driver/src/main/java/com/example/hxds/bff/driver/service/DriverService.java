package com.example.hxds.bff.driver.service;

import com.example.hxds.dr.controller.form.RegisterNewDriverForm;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-06 16:27
 **/
public interface DriverService {

    long registerNewDriver(RegisterNewDriverForm form);

}
