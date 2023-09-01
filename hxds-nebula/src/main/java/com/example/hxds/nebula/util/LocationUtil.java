package com.example.hxds.nebula.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author zhanglumin
 * @since 2023/9/1 12:39 PM
 */
public class LocationUtil {

    //地球半径
    private final static double EARTH_RADIUS = 6378.137;

    private static double rad(double d) {
        return d * Math.PI / 180.0;
    }

    /**
     * 计算两点间距离
     *
     * @return double 距离 单位公里,精确到米
     */
    public static double getDistance(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lng1) - rad(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * EARTH_RADIUS;
        s = new BigDecimal(s).setScale(3, RoundingMode.HALF_UP).doubleValue();
        return s;
    }
}
