package com.example.hxds.mis.api.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.mis.api.controller.form.SearchOrderByPageForm;
import com.example.hxds.mis.api.controller.form.SearchOrderLastGpsForm;

import java.util.List;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-05 16:27
 **/
public interface OrderService {

    PageUtils searchOrderByPage(SearchOrderByPageForm form);

    Map searchOrderComprehensiveInfo(long orderId);

    Map searchOrderLastGps(SearchOrderLastGpsForm form);

    List<Map> searchOrderStartLocationIn30Days();
}
