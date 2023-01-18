package com.example.hxds.odr.db.dao;

import com.example.hxds.odr.db.pojo.OrderEntity;

import java.util.HashMap;

public interface OrderDao {

    HashMap searchDriverTodayBusinessData(long driverId);

    int insert(OrderEntity entity);

    String searchOrderIdByUUID(String uuid);
}




