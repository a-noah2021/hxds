package com.example.hxds.mis.api.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.mis.api.controller.form.SearchDriverByPageForm;

import java.util.HashMap;


public interface DriverService {

    PageUtils searchDriverByPage(SearchDriverByPageForm form);

    HashMap searchDriverComprehensiveData(byte realAuth, Long driverId);


}

