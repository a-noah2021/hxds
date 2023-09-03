package com.example.hxds.dr.service.impl;

import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.dr.db.dao.WalletDao;
import com.example.hxds.dr.db.dao.WalletIncomeDao;
import com.example.hxds.dr.db.pojo.WalletIncomeEntity;
import com.example.hxds.dr.service.WalletIncomeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 17:43
 **/
@Service
@Slf4j
public class WalletIncomeServiceImpl implements WalletIncomeService {

    @Resource
    private WalletDao walletDao;

    @Resource
    private WalletIncomeDao walletIncomeDao;

    @Override
    @Transactional
    @LcnTransaction
    public int transfer(WalletIncomeEntity entity) {
        // 添加转账记录
        int rows = walletIncomeDao.insert(entity);
        if (rows != 1) {
            throw new HxdsException("添加转账记录失败");
        }
        HashMap param = new HashMap() {{
            put("driverId", entity.getDriverId());
            put("amount", entity.getAmount());
        }};
        // 更新账户余额
        rows = walletDao.updateWalletBalance(param);
        if (rows != 1) {
            throw new HxdsException("更新钱包余额失败");
        }
        return rows;
    }
}
