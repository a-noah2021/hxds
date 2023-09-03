package com.example.hxds.odr.feigin;

import com.example.hxds.common.util.R;
import com.example.hxds.odr.controller.form.TransferForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 18:15
 **/
@FeignClient(value = "hxds-dr")
public interface DrServiceApi {

    @PostMapping("/wallet/income/transfer")
    R transfer(TransferForm form);

}
