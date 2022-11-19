package com.example.hxds.cst.db.dao;

import com.example.hxds.cst.db.pojo.CustomerCarEntity;
import java.util.ArrayList;
import java.util.HashMap;

public interface CustomerCarDao {

    int insert(CustomerCarEntity entity);

    ArrayList<HashMap> searchCustomerCarList(long customerId);

    int deleteCustomerCarById(long id);

}




