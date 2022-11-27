package com.example.hxds.cst.db.dao;

import java.util.Map;

public interface CustomerDao {

    int registerNewCustomer(Map param);

    long hasCustomer(Map param);

    String searchCustomerId(String openId);

    String login(String openId);
}
