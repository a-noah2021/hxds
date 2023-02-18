package com.example.hxds.bff.customer.service.impl;

import com.example.hxds.bff.customer.controller.form.SearchOrderLocationCacheForm;
import com.example.hxds.bff.customer.feign.MpsServiceApi;
import com.example.hxds.bff.customer.service.OrderLocationService;
import com.example.hxds.common.util.R;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 00:07
 **/
@Service
public class OrderLocationServiceImpl implements OrderLocationService {

    @Resource
    private MpsServiceApi mpsServiceApi;

    @Override
    public HashMap searchOrderLocationCache(SearchOrderLocationCacheForm form) {
        R r = mpsServiceApi.searchOrderLocationCache(form);
        HashMap map = (HashMap) r.get("result");
        return map;
    }
}