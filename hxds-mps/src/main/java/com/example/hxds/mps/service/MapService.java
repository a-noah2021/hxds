package com.example.hxds.mps.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-12 22:19
 **/
public interface MapService {

    /**
     * @param mode
     * @param startPlaceLatitude  始发地纬度
     * @param startPlaceLongitude  始发地经度
     * @param endPlaceLatitude
     * @param endPlaceLongitude
     * @return
     */
    HashMap estimateOrderMileageAndMinute(String mode, String startPlaceLatitude,
                                          String startPlaceLongitude, String endPlaceLatitude, String endPlaceLongitude);

    HashMap calculateDriveLine(String startPlaceLatitude,String startPlaceLongitude,
                               String endPlaceLatitude,String endPlaceLongitude);


}
