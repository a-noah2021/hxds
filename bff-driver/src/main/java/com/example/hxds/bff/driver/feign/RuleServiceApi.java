package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.CalculateIncentiveFeeForm;
import com.example.hxds.bff.driver.controller.form.CalculateOrderChargeForm;
import com.example.hxds.bff.driver.controller.form.CalculateProfitsharingForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author zhanglumin
 * @since 2023/9/1 1:15 PM
 */
@FeignClient(value = "hxds-rule")
public interface RuleServiceApi {

    @PostMapping("/charge/calculateOrderCharge")
    R calculateOrderCharge(CalculateOrderChargeForm form);

    @PostMapping("/award/calculateIncentiveFee")
    R calculateIncentiveFee(CalculateIncentiveFeeForm form);

    @PostMapping("/profitsharing/calculateProfitsharing")
    R calculateProfitsharing(CalculateProfitsharingForm form);
}
