package com.example.hxds.odr.service.impl;

import com.example.hxds.odr.db.dao.OrderDao;
import com.example.hxds.odr.service.OrderService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-31 21:56
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OrderDao orderDao;

    @Override
    public HashMap searchDriverTodayBusinessData(long driverId) {
        return orderDao.searchDriverTodayBusinessData(driverId);
    }
}
