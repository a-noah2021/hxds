package com.example.hxds.dr.service;

import com.example.hxds.common.util.PageUtils;

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

    HashMap login(String code);

    HashMap searchDriverBaseInfo(long driverId);

    PageUtils searchDriverByPage(Map param);

    HashMap searchDriverAuth(long driverId);

    HashMap searchDriverRealSummary(long driverId);

    int updateDriverRealAuth(Map param);

    HashMap searchDriverBriefInfo(long driverId);
}
