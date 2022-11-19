package com.example.hxds.cst.service;

import com.example.hxds.cst.db.pojo.CustomerCarEntity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-20 00:29
 **/
public interface CustomerCarService {

    void insertCustomerCar(CustomerCarEntity entity);

    ArrayList<HashMap> searchCustomerCarList(long customerId);

    int deleteCustomerCarById(long id);

}
