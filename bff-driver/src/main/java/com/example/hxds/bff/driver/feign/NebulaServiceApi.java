package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.config.MultipartSupportConfig;
import com.example.hxds.bff.driver.controller.form.InsertOrderGpsForm;
import com.example.hxds.bff.driver.controller.form.InsertOrderMonitoringForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-02 21:10
 **/
@FeignClient(value = "hxds-nebula", configuration = MultipartSupportConfig.class)
public interface NebulaServiceApi {

    @PostMapping(value = "/monitoring/insertOrderMonitoring")
    R insertOrderMonitoring(InsertOrderMonitoringForm form);

    @PostMapping("/order/gps/insertOrderGps")
    R insertOrderGps(InsertOrderGpsForm form);
}
