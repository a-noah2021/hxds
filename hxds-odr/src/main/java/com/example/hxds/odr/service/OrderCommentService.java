package com.example.hxds.odr.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.odr.db.pojo.OrderCommentEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 00:50
 **/
public interface OrderCommentService {

    int insert(OrderCommentEntity entity);

    HashMap searchCommentByOrderId(Map param);

    PageUtils searchCommentByPage(Map param);
}
