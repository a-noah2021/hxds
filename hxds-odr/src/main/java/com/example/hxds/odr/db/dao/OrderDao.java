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

    List<String> searchOrderStartLocationIn30Days();

    int updateOrderMileageAndFee(Map param);

    long validDriverOwnOrder(Map param);

    Map searchSettlementNeedData(long orderId);

    HashMap searchOrderById(Map param);

    HashMap validCanPayOrder(Map param);

    int updateOrderPrepayId(Map param);

    HashMap searchOrderIdAndStatus(String uuid);

    HashMap searchDriverIdAndIncentiveFee(String uuid);

    int updateOrderPayIdAndStatus(Map param);

    int finishOrder(String uuid);

    HashMap searchUuidAndStatus(long orderId);

    int updateOrderAboutPayment(Map param);

    long validDriverAndCustomerOwnOrder(Map param);

    List<HashMap> searchDriverOrderByPage(Map param);

    long searchDriverOrderCount(Map param);

    List<HashMap> searchCustomerOrderByPage(Map param);

    long searchCustomerOrderCount(Map param);
}




