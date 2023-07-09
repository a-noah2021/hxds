package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.CalculateDriveLineForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-09 21:37
 **/
@FeignClient(value = "hxds-mps")
public interface MpsServiceApi {
    @PostMapping("/map/calculateDriveLine")
    R calculateDriveLine(CalculateDriveLineForm form);
}
