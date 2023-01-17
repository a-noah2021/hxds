package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.controller.form.EstimateOrderChargeForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "hxds-rule")
public interface RuleServiceApi {
    @PostMapping("/charge/estimateOrderCharge")
    R estimateOrderCharge(EstimateOrderChargeForm form);
}
