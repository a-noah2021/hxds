package com.example.hxds.nebula.db.dao;


import com.example.hxds.nebula.db.pojo.OrderVoiceTextEntity;

import java.util.Map;

public interface OrderVoiceTextDao {

    int insert(OrderVoiceTextEntity entity);

    Long searchIdByUuid(String uuid);

    int updateCheckResult(Map param);

}
