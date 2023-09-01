package com.example.hxds.nebula.db.dao;


import com.example.hxds.nebula.db.pojo.OrderGpsEntity;

import java.util.HashMap;
import java.util.List;

public interface OrderGpsDao {

    int insert(OrderGpsEntity orderGpsEntity);

    List<HashMap> searchOrderGps(long orderId);

    HashMap searchOrderLastGps(long orderId);

    List<HashMap> searchOrderAllGps(long orderId);
}
