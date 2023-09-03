package com.example.hxds.dr.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.MicroAppUtil;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.dr.db.dao.DriverDao;
import com.example.hxds.dr.db.dao.DriverSettingsDao;
import com.example.hxds.dr.db.dao.WalletDao;
import com.example.hxds.dr.db.pojo.DriverSettingsEntity;
import com.example.hxds.dr.db.pojo.WalletEntity;
import com.example.hxds.dr.service.DriverService;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonRequest;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

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

    @Value("${tencent.cloud.secretId}")
    private String secretId;

    @Value("${tencent.cloud.secretKey}")
    private String secretKey;

    @Value("${tencent.cloud.face.groupName}")
    private String groupName;

    @Value("${tencent.cloud.face.region}")
    private String region;

    /**
     * 前端传来的参数到微信校验是否注册过，若没注册过将存储必要信息落库
     *
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
        if (driverDao.hasDriver(tempParam) != 0) {
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

    @Override
    @Transactional
    @LcnTransaction
    public String createDriverFaceModel(long driverId, String photo) {
        HashMap map = driverDao.searchDriverNameAndSex(driverId);
        String name = MapUtil.getStr(map, "name");
        String sex = MapUtil.getStr(map, "sex");
        Credential cred = new Credential(secretId, secretKey); //import v20200303.models.CreatePersonRequest
        IaiClient client = new IaiClient(cred, region);
        try {
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(groupName);
            req.setPersonId(driverId + "");
            long gender = sex.equals("男") ? 1L : 2L;
            req.setGender(gender);
            req.setQualityControl(4L);  //照片质量等级
            req.setUniquePersonControl(4L); //重复人员识别等级
            req.setPersonName(name);
            req.setImage(photo); //base图片
            CreatePersonResponse resp = client.CreatePerson(req);
            if (StrUtil.isNotBlank(resp.getFaceId())) {
                int rows = driverDao.updateDriverArchive(driverId);
                if (rows != 1) {
                    return "更新司机归档字段失败";
                }
            }
        } catch (TencentCloudSDKException e) {
            log.error("创建腾讯云端司机档案失败", e);
            return "创建腾讯云端司机档案失败";
        }
        return "";
    }

    /**
     * 每天司机第一次接单时用当前微信用户登陆，同时判断是否人脸识别认证和当前手机号是否与注册手机号不一致
     *
     * @param code
     * @return
     */
    @Override
    public HashMap login(String code) { //, String phoneCode) {
        String openId = microAppUtil.getOpenId(code);
        HashMap result = driverDao.login(openId);
        // 之前已经注册过，同时还要判断是否经历过人脸识别认证
        if (result != null && result.containsKey("archive")) {
            int temp = MapUtil.getInt(result, "archive");
            boolean archive = (temp == 1) ? true : false;
            // HashMap.replace(K key, V oldValue, V newValue) 如果不存oldValue，则替换 key 对应的值，返回 key 对应的旧值(可能为null)
            // 如果存oldValue，key对应的旧值相等且替换成功返回 true，如果key不存在或key对应的旧值不相等，则返回 false。
            result.replace("archive", archive);
        }
        return result;
    }

    @Override
    public HashMap searchDriverBaseInfo(long driverId) {
        HashMap result = driverDao.searchDriverBaseInfo(driverId);
        JSONObject summary = JSONUtil.parseObj(MapUtil.getStr(result, "summary")); //将摘要信息转换成JSON对象
        result.replace("summary", summary);
        return result;
    }

    @Override
    public PageUtils searchDriverByPage(Map param) {
        long count = driverDao.searchDriverCount(param);
        ArrayList<HashMap> list = null;
        if(count == 0){
            list = new ArrayList<>();
        }else{
            list = driverDao.searchDriverByPage(param);
        }
        Integer start = (Integer) param.get("start");
        Integer length = (Integer) param.get("length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public HashMap searchDriverAuth(long driverId) {
        HashMap map = driverDao.searchDriverAuth(driverId);
        return map;
    }

    @Override
    public HashMap searchDriverRealSummary(long driverId) {
        HashMap map = driverDao.searchDriverRealSummary(driverId);
        return map;
    }

    @Override
    @LcnTransaction
    @Transactional
    public int updateDriverRealAuth(Map param) {
        int rows = driverDao.updateDriverRealAuth(param);
        return rows;
    }

    @Override
    public HashMap searchDriverBriefInfo(long driverId) {
        HashMap map = driverDao.searchDriverBriefInfo(driverId);
        return map;
    }

    @Override
    public String searchDriverOpenId(long driverId) {
        String openId = driverDao.searchDriverOpenId(driverId);
        return openId;
    }

}
