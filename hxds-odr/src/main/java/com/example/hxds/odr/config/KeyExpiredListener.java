package com.example.hxds.odr.config;

import com.example.hxds.odr.db.dao.OrderBillDao;
import com.example.hxds.odr.db.dao.OrderDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-14 21:59
 **/
@Slf4j
@Component
public class KeyExpiredListener extends KeyExpirationEventMessageListener {

    @Resource
    private OrderDao orderDao;

    @Resource
    private OrderBillDao orderBillDao;

    public KeyExpiredListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {
        // 从消息队列中接收消息
        if(new String(message.getChannel()).equals("__keyevent@5__:expired")){
            // 反系列化Key，否则出现乱码
            JdkSerializationRedisSerializer serializer=new JdkSerializationRedisSerializer();
            String key = serializer.deserialize(message.getBody()).toString();
            if(key.contains("order#")){
                long orderId = Long.parseLong(key.split("#")[1]);
                HashMap param=new HashMap(){{
                    put("orderId",orderId);
                }};
                int rows = orderDao.deleteUnAcceptOrder(param);
                if(rows==1){
                    log.info("删除了无人接单的订单：" + orderId);
                }
                rows = orderBillDao.deleteUnAcceptOrderBill(orderId);
                if(rows==1){
                    log.info("删除了无人接单的账单：" + orderId);
                }
            }
        }
        super.onMessage(message, pattern);
    }
}