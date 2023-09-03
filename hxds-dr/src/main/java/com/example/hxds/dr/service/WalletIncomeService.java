package com.example.hxds.dr.service;

import com.example.hxds.dr.db.pojo.WalletIncomeEntity;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 17:42
 **/
public interface WalletIncomeService {

    int transfer(WalletIncomeEntity entity);

}
