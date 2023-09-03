package com.example.hxds.vhr.service.impl;

import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.codingapi.txlcn.tc.annotation.TccTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.vhr.db.dao.VoucherCustomerDao;
import com.example.hxds.vhr.service.VoucherCustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-02 16:22
 **/
@Service
public class VoucherCustomerServiceImpl implements VoucherCustomerService {

    @Resource
    private VoucherCustomerDao voucherCustomerDao;

    @Override
    @Transactional
    @LcnTransaction
    public String useVoucher(Map param) {
        String discount = voucherCustomerDao.validCanUseVoucher(param);
        if (!Objects.isNull(discount)) {
            int rows = voucherCustomerDao.bindVoucher(param);
            if (rows != 1) {
                throw new HxdsException("代金券不可用");
            }
            return discount;
        }
        throw new HxdsException("代金券不可用");
    }
}
