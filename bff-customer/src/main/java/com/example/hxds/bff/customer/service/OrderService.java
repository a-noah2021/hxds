package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.CreateNewOrderForm;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 00:12
 **/
public interface OrderService {
    HashMap createNewOrder(CreateNewOrderForm form);
}