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
 * @date: 2023-02-14 21:53
 **/
@Configuration
public class RedisConfiguration {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 自定义Redis队列的名字，如果有缓存销毁，就自动往这个队列中发消息
     * 每个子系统有各自的Redis逻辑库，订单子系统不会监听到其他子系统缓存数据销毁
     */
    @Bean
    public ChannelTopic expiredTopic() {
        return new ChannelTopic("__keyevent@5__:expired");  // 选择5号数据库
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        return redisMessageListenerContainer;
    }
}