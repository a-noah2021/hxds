package com.example.hxds.mps.service;

import java.util.List;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-27 23:13
 **/
public interface DriverLocationService {

    void updateLocationCache(Map param);

    void removeLocationCache(long driverId);

    List searchBefittingDriverAboutOrder(double startPlaceLatitude,
                     double startPlaceLongitude, double endPlaceLatitude,
                     double endPlaceLongitude, double mileage);
}
