package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchOrderByPageForm;
import com.example.hxds.mis.api.controller.form.SearchOrderContentForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-05 16:25
 **/
@FeignClient(value = "hxds-odr")
public interface OdrServiceApi {

    @PostMapping("/order/searchOrderByPage")
    R searchOrderByPage(SearchOrderByPageForm form);

    @PostMapping("/order/searchOrderContent")
    R searchOrderContent(SearchOrderContentForm form);
}
