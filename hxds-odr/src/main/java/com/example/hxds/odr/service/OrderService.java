package com.example.hxds.odr.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.odr.db.pojo.OrderBillEntity;
import com.example.hxds.odr.db.pojo.OrderEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-31 21:55
 **/
public interface OrderService {

    HashMap searchDriverTodayBusinessData(long driverId);

    String insertOrder(OrderEntity orderEntity, OrderBillEntity orderBillEntity);

    String acceptNewOrder(long driverId, long orderId);

    HashMap searchDriverExecuteOrder(Map param);

    Integer searchOrderStatus(Map param);

    String deleteUnAcceptOrder(Map param);

    HashMap searchDriverCurrentOrder(long driverId);

    HashMap hasCustomerCurrentOrder(long customerId);

    HashMap searchOrderForMoveById(Map param);

    int arriveStartPlace(Map param);

    boolean confirmArriveStartPlace(long orderId);

    int startDriving(Map param);

    int updateOrderStatus(Map param);

    PageUtils searchOrderByPage(Map param);

    HashMap searchOrderContent(long orderId);

    List<Map> searchOrderStartLocationIn30Days();

    boolean validDriverOwnOrder(Map param);

    Map searchSettlementNeedData(long orderId);

    HashMap searchOrderById(Map param);

    HashMap validCanPayOrder(Map param);

    int updateOrderPrepayId(Map param);

    void handlePayment(String uuid, String payId, String driverOpenId, String payTime);
}
