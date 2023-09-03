package com.example.hxds.odr.db.dao;

import com.example.hxds.odr.db.pojo.OrderBillEntity;

import java.util.HashMap;
import java.util.Map;


public interface OrderBillDao {

    int insert(OrderBillEntity entity);

    int deleteUnAcceptOrderBill(long orderId);

    int updateBillFee(Map param);

    HashMap searchReviewDriverOrderBill(Map param);

    int updateBillPayment(Map param);
}




