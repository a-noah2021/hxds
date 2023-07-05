package com.example.hxds.nebula.service.impl;

import com.example.hxds.nebula.controller.vo.InsertOrderGpsVO;
import com.example.hxds.nebula.db.dao.OrderGpsDao;
import com.example.hxds.nebula.db.pojo.OrderGpsEntity;
import com.example.hxds.nebula.service.OrderGpsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-04 21:18
 **/
@Service
public class OrderGpsServiceImpl implements OrderGpsService {

    @Resource
    private OrderGpsDao orderGpsDao;

    @Override
    @Transactional
    public int insertOrderGps(List<InsertOrderGpsVO> list) {
        int rows = 0;
        for (OrderGpsEntity entity : list) {
            rows += orderGpsDao.insert(entity);
        }
        return rows;
    }

    @Override
    public List<HashMap> searchOrderGps(long orderId) {
        List<HashMap> list = orderGpsDao.searchOrderGps(orderId);
        return list;
    }

    @Override
    public HashMap searchOrderLastGps(long orderId) {
        HashMap map = orderGpsDao.searchOrderLastGps(orderId);
        return map;
    }
}
