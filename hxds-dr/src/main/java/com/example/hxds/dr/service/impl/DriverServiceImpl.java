package com.example.hxds.dr.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONObject;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.MicroAppUtil;
import com.example.hxds.dr.db.dao.DriverDao;
import com.example.hxds.dr.db.dao.DriverSettingsDao;
import com.example.hxds.dr.db.dao.WalletDao;
import com.example.hxds.dr.db.pojo.DriverSettingsEntity;
import com.example.hxds.dr.db.pojo.WalletEntity;
import com.example.hxds.dr.service.DriverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-05 19:43
 **/
@Service
@Slf4j
public class DriverServiceImpl implements DriverService {

    @Resource
    private MicroAppUtil microAppUtil;

    @Resource
    private DriverDao driverDao;

    @Resource
    private DriverSettingsDao settingsDao;

    @Resource
    private WalletDao walletDao;

    /**
     * 前端传来的参数到微信校验是否注册过，若没注册过将存储必要信息落库
     * @param param
     * @return
     */
    @Override
    @Transactional
    @LcnTransaction
    public String registerNewDriver(Map param) {
        // 前端传来的json参数里的code凭证，通过code凭证换取小程序用户登录态信息(openId、session_key)
        String code = MapUtil.getStr(param, "code");
        String openId = microAppUtil.getOpenId(code);
        // 传递Map封装的openId用于判断当前司机是否注册过
        HashMap tempParam = new HashMap() {{
            put("openId", openId);
        }};
        if(driverDao.hasDriver(tempParam)!=0){
            throw new HxdsException("该微信已完成注册");
        }
        // 未注册过，将凭证、登陆态信息落库
        param.put("openId", openId);
        driverDao.registerNewDriver(param);
        // 获取driverId，然后存储进DriverSettings和Wallet
        String driverId = driverDao.searchDriverId(openId);
        DriverSettingsEntity settingsEntity = new DriverSettingsEntity();
        settingsEntity.setDriverId(Long.parseLong(driverId));
        JSONObject json = new JSONObject();
        json.set("orientation", ""); // 定向接单
        json.set("listenService", true); // 自动听单
        json.set("orderDistance", 0); // 代驾订单预估里程不限，司机不挑单
        json.set("rangeDistance", 5); // 接受距离司机5公里以内单代驾单
        json.set("autoAccept", false); // 自动抢单
        settingsEntity.setSettings(json.toString());
        settingsDao.insertDriverSettings(settingsEntity);

        WalletEntity walletEntity = new WalletEntity();
        walletEntity.setDriverId(Long.parseLong(driverId));
        walletEntity.setBalance(new BigDecimal("0"));
        walletEntity.setPassword(null);
        walletDao.insert(walletEntity);
        return driverId; // BFF需要driverId颁布token
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateDriverAuth(Map param) {
        int rows = driverDao.updateDriverAuth(param);
        return rows;
    }

}
