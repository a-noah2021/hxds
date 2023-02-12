package com.example.hxds.cst.service.impl;

import cn.hutool.core.map.MapUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.MicroAppUtil;
import com.example.hxds.cst.db.dao.CustomerDao;
import com.example.hxds.cst.service.CustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class CustomerServiceImpl implements CustomerService {

    @Resource
    private CustomerDao customerDao;

    @Resource
    private MicroAppUtil microAppUtil;

    @Override
    @Transactional
    @LcnTransaction
    public String registerNewCustomer(Map param) {
        String code = MapUtil.getStr(param, "code");
        String openId = microAppUtil.getOpenId(code);
        HashMap map = new HashMap<>() {{
            put("openId", openId);
        }};
        if (customerDao.hasCustomer(map) != 0) {
            throw new HxdsException("该微信已经注册");
        }

        param.put("openId", openId);
        customerDao.registerNewCustomer(param);

        String customerId = customerDao.searchCustomerId(openId);
        return customerId;
    }

    @Override
    public String login(String code) {
        String openId = microAppUtil.getOpenId(code);
        String customerId = customerDao.login(openId);
        customerId = (customerId != null ? customerId : "");
        return customerId;
    }

    @Override
    public HashMap searchCustomerInfoInOrder(long customerId) {
        HashMap map = customerDao.searchCustomerInfoInOrder(customerId);
        return map;
    }
}
