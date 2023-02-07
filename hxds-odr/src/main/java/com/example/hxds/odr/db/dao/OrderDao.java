package com.example.hxds.odr.db.dao;

import com.example.hxds.odr.db.pojo.OrderEntity;

import java.util.HashMap;
import java.util.Map;

public interface OrderDao {

    HashMap searchDriverTodayBusinessData(long driverId);

    int insert(OrderEntity entity);

    String searchOrderIdByUUID(String uuid);

    int acceptNewOrder(Map param);

    HashMap searchDriverExecuteOrder(Map param);
}




