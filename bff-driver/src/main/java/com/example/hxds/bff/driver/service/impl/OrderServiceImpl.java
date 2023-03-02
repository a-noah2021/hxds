package com.example.hxds.bff.driver.service.impl;

import cn.hutool.core.map.MapUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.bff.driver.controller.form.*;
import com.example.hxds.bff.driver.feign.CstServiceApi;
import com.example.hxds.bff.driver.feign.NebulaServiceApi;
import com.example.hxds.bff.driver.feign.OdrServiceApi;
import com.example.hxds.bff.driver.service.OrderService;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.R;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-07 23:26
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OdrServiceApi odrServiceApi;

    @Resource
    private CstServiceApi cstServiceApi;

    @Resource
    private NebulaServiceApi nebulaServiceApi;

    @Override
    @Transactional
    @LcnTransaction
    public String acceptNewOrder(AcceptNewOrderForm form) {
        R r = odrServiceApi.acceptNewOrder(form);
        String result = MapUtil.getStr(r, "result");
        return result;
    }

    @Override
    public HashMap searchDriverExecuteOrder(SearchDriverExecuteOrderForm form) {
        // 查询订单信息
        R r = odrServiceApi.searchDriverExecuteOrder(form);
        HashMap orderMap = (HashMap) r.get("result");
        // 查询代驾客户信息
        Long customerId = MapUtil.getLong(orderMap, "customerId");
        SearchCustomerInfoInOrderForm customerInfoInOrderForm = new SearchCustomerInfoInOrderForm();
        customerInfoInOrderForm.setCustomerId(customerId);
        r = cstServiceApi.searchCustomerInfoInOrder(customerInfoInOrderForm);
        HashMap cstMap = (HashMap) r.get("result");

        HashMap map = new HashMap();
        map.putAll(orderMap);
        map.putAll(cstMap);
        return map;
    }

    @Override
    public HashMap searchDriverCurrentOrder(SearchDriverCurrentOrderForm form) {
        // 查询订单信息
        R r = odrServiceApi.searchDriverCurrentOrder(form);
        HashMap orderMap = (HashMap) r.get("result");
        if (MapUtil.isNotEmpty(orderMap)) {
            var map = new HashMap();
            // 查询代驾客户信息
            long customerId = MapUtil.getLong(orderMap, "customerId");
            SearchCustomerInfoInOrderForm customerInfoInOrderForm = new SearchCustomerInfoInOrderForm();
            customerInfoInOrderForm.setCustomerId(customerId);
            r = cstServiceApi.searchCustomerInfoInOrder(customerInfoInOrderForm);
            HashMap cstMap = (HashMap) r.get("result");
            map.putAll(orderMap);
            map.putAll(cstMap);
            return map;
        } else {
            return null;
        }
    }

    @Override
    public HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form) {
        R r = odrServiceApi.searchOrderForMoveById(form);
        HashMap map = (HashMap) r.get("result");
        return map;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int arriveStartPlace(ArriveStartPlaceForm form) {
        R r = odrServiceApi.arriveStartPlace(form);
        int rows = MapUtil.getInt(r, "rows");
        if (rows == 1) {
            //TODO 发送通知消息
        }
        return rows;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int startDriving(StartDrivingForm form) {
        R r = odrServiceApi.startDriving(form);
        int rows = MapUtil.getInt(r, "rows");
        if (rows == 1){
            InsertOrderMonitoringForm monitoringForm = new InsertOrderMonitoringForm();
            monitoringForm.setOrderId(form.getOrderId());
            nebulaServiceApi.insertOrderMonitoring(monitoringForm);
            // TODO 发送通知消息
        }
        return rows;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateOrderStatus(UpdateOrderStatusForm form) {
        R r = odrServiceApi.updateOrderStatus(form);
        int rows = MapUtil.getInt(r, "rows");
        // TODO:判断订单的状态，然后实现后续业务
        return rows;
    }
}
