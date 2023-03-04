package com.example.hxds.nebula.db.dao;


import com.example.hxds.nebula.db.pojo.OrderMonitoringEntity;

import java.util.HashMap;

public interface OrderMonitoringDao {

    int insert(long orderId);

    HashMap searchOrderRecordsAndReviews(long orderId);

    int updateOrderMonitoring(OrderMonitoringEntity entity);
}
