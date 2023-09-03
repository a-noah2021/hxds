package com.example.hxds.cst.service;

import java.util.HashMap;
import java.util.Map;

public interface CustomerService {

    String registerNewCustomer(Map param);

    String login(String code);

    HashMap searchCustomerInfoInOrder(long customerId);

    HashMap searchCustomerBriefInfo(long customerId);

    String searchCustomerOpenId(long customerId);
}

