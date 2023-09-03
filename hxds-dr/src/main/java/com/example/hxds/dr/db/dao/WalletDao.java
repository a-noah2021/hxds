package com.example.hxds.dr.db.dao;


import com.example.hxds.dr.db.pojo.WalletEntity;

import java.util.HashMap;
import java.util.Map;

public interface WalletDao {

    int insert(WalletEntity entity);

    int updateWalletBalance(Map param);

}




