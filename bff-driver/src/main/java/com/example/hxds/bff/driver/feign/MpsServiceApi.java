package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.RemoveLocationCacheForm;
import com.example.hxds.bff.driver.controller.form.UpdateLocationCacheForm;
import com.example.hxds.bff.driver.controller.form.UpdateOrderLocationCacheForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-17 23:11
 **/
@FeignClient(value = "hxds-mps")
public interface MpsServiceApi {

    @PostMapping("/driver/location/removeLocationCache")
    R removeLocationCache(RemoveLocationCacheForm form);

    @PostMapping("/driver/location/updateLocationCache")
    R updateLocationCache(UpdateLocationCacheForm form);

    @PostMapping("/driver/location/updateOrderLocationCache")
    R updateOrderLocationCache(UpdateOrderLocationCacheForm form);
}
