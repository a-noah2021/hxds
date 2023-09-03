package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.controller.form.UseVoucherForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 22:48
 **/
@FeignClient(value = "hxds-vhr")
public interface VhrServiceApi {

    @PostMapping("/voucher/customer/useVoucher")
    R useVoucher(UseVoucherForm form);

}
