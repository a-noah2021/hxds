package com.example.hxds.mis.api.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchDriverByPageForm;
import com.example.hxds.mis.api.feign.DrServiceApi;
import com.example.hxds.mis.api.service.DriverService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-01 22:47
 **/
@Service
public class DriverServiceImpl implements DriverService {

    @Resource
    private DrServiceApi drServiceApi;

    @Override
    public PageUtils searchDriverByPage(SearchDriverByPageForm form) {
        R r = drServiceApi.searchDriverByPage(form);
        PageUtils pageUtils = BeanUtil.toBean(r.get("result"), PageUtils.class);
        return pageUtils;
    }
}
