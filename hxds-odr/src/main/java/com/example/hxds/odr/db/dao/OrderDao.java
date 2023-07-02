package com.example.hxds.odr.db.dao;

import com.example.hxds.odr.db.pojo.OrderEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface OrderDao {

    HashMap searchDriverTodayBusinessData(long driverId);

    int insert(OrderEntity entity);

    String searchOrderIdByUUID(String uuid);

    int acceptNewOrder(Map param);

    HashMap searchDriverExecuteOrder(Map param);

    Integer searchOrderStatus(Map param);

    int deleteUnAcceptOrder(Map param);

    HashMap searchDriverCurrentOrder(long driverId);

    long hasCustomerUnFinishedOrder(long customerId);

    HashMap hasCustomerUnAcceptOrder(long customerId);

    HashMap searchOrderForMoveById(Map param);

    int updateOrderStatus(Map param);

    long searchOrderCount(Map param);

    List<HashMap> searchOrderByPage(Map param);

    HashMap searchOrderContent(long orderId);
}




