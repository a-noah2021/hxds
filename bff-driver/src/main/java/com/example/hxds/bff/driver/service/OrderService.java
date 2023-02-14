package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.AcceptNewOrderForm;
import com.example.hxds.bff.driver.controller.form.SearchDriverCurrentOrderForm;
import com.example.hxds.bff.driver.controller.form.SearchDriverExecuteOrderForm;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-07 23:24
 **/
public interface OrderService {

    String acceptNewOrder(AcceptNewOrderForm form);

    HashMap searchDriverExecuteOrder(SearchDriverExecuteOrderForm form);

    HashMap searchDriverCurrentOrder(SearchDriverCurrentOrderForm form);

}
