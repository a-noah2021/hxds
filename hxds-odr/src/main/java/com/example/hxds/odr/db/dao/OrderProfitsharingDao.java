package com.example.hxds.odr.db.dao;


import com.example.hxds.odr.db.pojo.OrderProfitsharingEntity;

import java.util.ArrayList;
import java.util.HashMap;

public interface OrderProfitsharingDao {

    int insert (OrderProfitsharingEntity entity);

    HashMap searchDriverIncome(String uuid);

    int updateProfitsharingStatus(long profitsharingId);
}




