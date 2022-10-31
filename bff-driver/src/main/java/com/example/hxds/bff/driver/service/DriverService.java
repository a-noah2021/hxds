package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.*;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-06 16:27
 **/
public interface DriverService {

    long registerNewDriver(RegisterNewDriverForm form);

    int updateDriverAuth(UpdateDriverAuthForm form);

    String createDriverFaceModel(CreateDriverFaceModelForm form);

    HashMap login(LoginForm form);

    HashMap searchDriverBaseInfo(SearchDriverBaseInfoForm form);

    HashMap searchWorkbenchData(long driverId);
}
