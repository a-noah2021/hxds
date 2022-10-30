package com.example.hxds.dr.service;

import java.util.HashMap;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-05 19:42
 **/
public interface DriverService {

    String registerNewDriver(Map param);

    int updateDriverAuth(Map param);

    String createDriverFaceModel(long driverId, String photo);

    HashMap login(String code); //, String phoneCode);

    HashMap searchDriverBaseInfo(long driverId);
}
