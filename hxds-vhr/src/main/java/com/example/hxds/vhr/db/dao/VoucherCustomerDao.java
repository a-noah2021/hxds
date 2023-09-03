package com.example.hxds.vhr.db.dao;


import com.example.hxds.vhr.db.pojo.VoucherCustomerEntity;

import java.util.Map;

public interface VoucherCustomerDao {
    int insert(VoucherCustomerEntity entity);

    String validCanUseVoucher(Map param);

    int bindVoucher(Map param);
}




