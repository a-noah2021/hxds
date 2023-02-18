package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.SearchOrderLocationCacheForm;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 00:06
 **/
public interface OrderLocationService {
    HashMap searchOrderLocationCache(SearchOrderLocationCacheForm form);
}
