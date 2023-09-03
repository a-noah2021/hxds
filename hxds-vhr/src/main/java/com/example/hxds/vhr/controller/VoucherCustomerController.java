package com.example.hxds.vhr.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.R;
import com.example.hxds.vhr.controller.form.UseVoucherForm;
import com.example.hxds.vhr.service.VoucherCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 16:30
 **/
@RestController
@RequestMapping("/voucher/customer")
@Tag(name = "VoucherCustomerController", description = "代金券客户Web接口")
public class VoucherCustomerController {
    @Resource
    private VoucherCustomerService voucherCustomerService;

    @PostMapping("/useVoucher")
    @Operation(summary = "检查可否使用代金券")
    public R useVoucher(@RequestBody @Valid UseVoucherForm form){
        Map param = BeanUtil.beanToMap(form);
        String discount = voucherCustomerService.useVoucher(param);
        return R.ok().put("result",discount);
    }
}