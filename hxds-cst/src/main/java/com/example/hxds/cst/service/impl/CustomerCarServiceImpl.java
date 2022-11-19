package com.example.hxds.cst.service.impl;

import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.cst.db.dao.CustomerCarDao;
import com.example.hxds.cst.db.pojo.CustomerCarEntity;
import com.example.hxds.cst.service.CustomerCarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-20 00:29
 **/
@Service
public class CustomerCarServiceImpl implements CustomerCarService {

    @Resource
    private CustomerCarDao customerCarDao;

    @Override
    @Transactional
    @LcnTransaction
    public void insertCustomerCar(CustomerCarEntity entity) {
        customerCarDao.insert(entity);
    }

    @Override
    public ArrayList<HashMap> searchCustomerCarList(long customerId) {
        ArrayList list = customerCarDao.searchCustomerCarList(customerId);
        return list;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int deleteCustomerCarById(long id) {
        int rows = customerCarDao.deleteCustomerCarById(id);
        return rows;
    }
}
