package com.example.hxds.dr.db.dao;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface DriverDao {

    int registerNewDriver(Map param);

    long hasDriver(Map param);

    String searchDriverId(String openId);

    int updateDriverAuth(Map param);

    HashMap searchDriverNameAndSex(long driverId);

    /**
     * archive:是否在腾讯云归档存放司机面部信息 0未录入，1录入
     * 更新tb_driver的archive字段0->1
     * @param driverId
     * @return
     */
    int updateDriverArchive(long driverId);

    /**
     * status:状态，1正常，2禁用，3.降低接单量
     * 根据openId筛选status不是2的司机用户
     * @param openId
     * @return
     */
    HashMap login(String openId);

    HashMap searchDriverBaseInfo(long driverId);

    ArrayList<HashMap> searchDriverByPage(Map param);

    long searchDriverCount(Map param);


}




