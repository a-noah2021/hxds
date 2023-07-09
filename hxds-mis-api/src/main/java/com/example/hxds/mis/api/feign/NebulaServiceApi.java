package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchOrderGpsForm;
import com.example.hxds.mis.api.controller.form.SearchOrderLastGpsForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-09 21:41
 **/
@FeignClient(value = "hxds-nebula")
public interface NebulaServiceApi {
    @PostMapping("/order/gps/searchOrderGps")
    R searchOrderGps(SearchOrderGpsForm form);

    @PostMapping("/order/gps/searchOrderLastGps")
    R searchOrderLastGps(SearchOrderLastGpsForm form);
}
