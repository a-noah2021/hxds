package com.example.hxds.dr.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.R;
import com.example.hxds.dr.controller.form.TransferForm;
import com.example.hxds.dr.db.pojo.WalletIncomeEntity;
import com.example.hxds.dr.service.WalletIncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 17:50
 **/
@RestController
@RequestMapping("/wallet/income")
@Tag(name = "WalletIncomeController", description = "司机钱包入账Web接口")
public class WalletIncomeController {

    @Resource
    private WalletIncomeService walletIncomeService;

    @PostMapping("/transfer")
    @Operation(summary = "转账")
    public R transfer(@RequestBody @Valid TransferForm form) {
        WalletIncomeEntity entity = BeanUtil.toBean(form, WalletIncomeEntity.class);
        entity.setStatus((byte) 3);
        int rows = walletIncomeService.transfer(entity);
        return R.ok().put("rows", rows);
    }
}

