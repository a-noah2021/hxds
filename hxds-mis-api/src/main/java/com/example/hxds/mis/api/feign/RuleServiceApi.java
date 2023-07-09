package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchCancelRuleByIdForm;
import com.example.hxds.mis.api.controller.form.SearchChargeRuleByIdForm;
import com.example.hxds.mis.api.controller.form.SearchProfitsharingRuleByIdForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-07-09 21:34
 **/
@FeignClient(value = "hxds-rule")
public interface RuleServiceApi {
    @PostMapping("/charge/searchChargeRuleById")
    R searchChargeRuleById(SearchChargeRuleByIdForm form);

    @PostMapping("/cancel/searchCancelRuleById")
    R searchCancelRuleById(SearchCancelRuleByIdForm form);

    @PostMapping("/profitsharing/searchProfitsharingRuleById")
    R searchProfitsharingRuleById(SearchProfitsharingRuleByIdForm form);
}
