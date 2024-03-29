package com.example.hxds.nebula.service;

import com.example.hxds.nebula.controller.vo.InsertOrderGpsVO;

import java.util.HashMap;
import java.util.List;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-04 21:17
 **/
public interface OrderGpsService {

    int insertOrderGps(List<InsertOrderGpsVO> list);

    List<HashMap> searchOrderGps(long orderId);

    HashMap searchOrderLastGps(long orderId);

    String calculateOrderMileage(long orderId);
}
