package com.example.hxds.mis.api.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.*;
import com.example.hxds.mis.api.feign.*;
import com.example.hxds.mis.api.service.OrderService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-05 16:28
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OdrServiceApi odrServiceApi;

    @Resource
    private MpsServiceApi mpsServiceApi;

    @Resource
    private NebulaServiceApi nebulaServiceApi;

    @Resource
    private CstServiceApi cstServiceApi;

    @Resource
    private DrServiceApi drServiceApi;

    @Resource
    private RuleServiceApi ruleServiceApi;

    @Override
    public PageUtils searchOrderByPage(SearchOrderByPageForm form) {
        R r = odrServiceApi.searchOrderByPage(form);
        PageUtils pageUtils = BeanUtil.toBean(r.get("result"), PageUtils.class);
        return pageUtils;
    }

    @Override
    public Map searchOrderComprehensiveInfo(long orderId) {
        Map map = new HashMap();

        SearchOrderContentForm form_1 = new SearchOrderContentForm();
        form_1.setOrderId(orderId);
        R r = odrServiceApi.searchOrderContent(form_1);
        if (!r.containsKey("result")) {
            throw new HxdsException("不存在订单记录");
        }
        HashMap content = (HashMap) r.get("result");
        map.put("content", content);

        long customerId = MapUtil.getLong(content, "customerId");
        SearchCustomerBriefInfoForm form_2 = new SearchCustomerBriefInfoForm();
        form_2.setCustomerId(customerId);
        r = cstServiceApi.searchCustomerBriefInfo(form_2);
        HashMap customerInfo = (HashMap) r.get("result");
        map.put("customerInfo", customerInfo);

        long driverId = MapUtil.getLong(content, "driverId");
        SearchDriverBriefInfoForm form_3 = new SearchDriverBriefInfoForm();
        form_3.setDriverId(driverId);
        r = drServiceApi.searchDriverBriefInfo(form_3);
        HashMap driverInfo = (HashMap) r.get("result");
        map.put("driverInfo", driverInfo);

        if (content.containsKey("chargeRuleId")) {
            long chargeRuleId = MapUtil.getLong(content, "chargeRuleId");
            SearchChargeRuleByIdForm form_4 = new SearchChargeRuleByIdForm();
            form_4.setRuleId(chargeRuleId);
            r = ruleServiceApi.searchChargeRuleById(form_4);
            HashMap chargeRule = (HashMap) r.get("result");
            map.put("chargeRule", chargeRule);
        }

        if (content.containsKey("cancelRuleId")) {
            long cancelRuleId = MapUtil.getLong(content, "cancelRuleId");
            SearchCancelRuleByIdForm form_5 = new SearchCancelRuleByIdForm();
            form_5.setRuleId(cancelRuleId);
            r = ruleServiceApi.searchCancelRuleById(form_5);
            HashMap cancelRule = (HashMap) r.get("result");
            map.put("cancelRule", cancelRule);
        }

        if (content.containsKey("profitsharingRuleId")) {
            long profitsharingRuleId = MapUtil.getLong(content, "profitsharingRuleId");
            SearchProfitsharingRuleByIdForm form_6 = new SearchProfitsharingRuleByIdForm();
            form_6.setRuleId(profitsharingRuleId);
            r = ruleServiceApi.searchProfitsharingRuleById(form_6);
            HashMap profitsharingRule = (HashMap) r.get("result");
            map.put("profitsharingRule", profitsharingRule);
        }

        CalculateDriveLineForm form_7 = new CalculateDriveLineForm();
        HashMap startPlaceLocation = (HashMap) content.get("startPlaceLocation");
        HashMap endPlaceLocation = (HashMap) content.get("endPlaceLocation");
        form_7.setStartPlaceLatitude(MapUtil.getStr(startPlaceLocation, "latitude"));
        form_7.setStartPlaceLongitude(MapUtil.getStr(startPlaceLocation, "longitude"));
        form_7.setEndPlaceLatitude(MapUtil.getStr(endPlaceLocation, "latitude"));
        form_7.setEndPlaceLongitude(MapUtil.getStr(endPlaceLocation, "longitude"));
        r = mpsServiceApi.calculateDriveLine(form_7);
        HashMap driveLine = (HashMap) r.get("result");
        map.put("driveLine", driveLine);

        int status = MapUtil.getInt(content, "status");
        if (status >= 5 && status <= 8) {
            SearchOrderGpsForm form_8 = new SearchOrderGpsForm();
            form_8.setOrderId(orderId);
            r = nebulaServiceApi.searchOrderGps(form_8);
            ArrayList<HashMap> orderGps = (ArrayList<HashMap>) r.get("result");
            map.put("orderGps", orderGps);
        } else if (status == 4) {
            SearchOrderLastGpsForm form_9 = new SearchOrderLastGpsForm();
            form_9.setOrderId(orderId);
            r = nebulaServiceApi.searchOrderLastGps(form_9);
            HashMap lastGps = (HashMap) r.get("result");
            map.put("lastGps", lastGps);
        }
        return map;
    }
}
