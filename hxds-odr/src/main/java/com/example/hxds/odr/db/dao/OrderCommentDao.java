package com.example.hxds.odr.db.dao;


import com.example.hxds.odr.db.pojo.OrderCommentEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface OrderCommentDao {

    int insert(OrderCommentEntity entity);

    HashMap searchCommentByOrderId(Map param);

    List<HashMap> searchCommentByPage(Map param);

    long searchCommentCount(Map param);
}




