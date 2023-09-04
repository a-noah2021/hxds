package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.*;
import com.example.hxds.common.util.PageUtils;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-07 23:24
 **/
public interface OrderService {

    String acceptNewOrder(AcceptNewOrderForm form);

    HashMap searchDriverExecuteOrder(SearchDriverExecuteOrderForm form);

    HashMap searchDriverCurrentOrder(SearchDriverCurrentOrderForm form);

    HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form);

    int arriveStartPlace(ArriveStartPlaceForm form);

    int startDriving(StartDrivingForm form);

    int updateOrderStatus(UpdateOrderStatusForm form);

    int updateOrderBill(UpdateBillFeeForm form);

    HashMap searchReviewDriverOrderBill(SearchReviewDriverOrderBillForm form);

    Integer searchOrderStatus(SearchOrderStatusForm form);

    String updateOrderAboutPayment(long driverId, UpdateOrderAboutPaymentForm form);

    PageUtils searchDriverOrderByPage(SearchDriverOrderByPageForm form);

    HashMap searchOrderById(SearchOrderByIdForm form);
}
