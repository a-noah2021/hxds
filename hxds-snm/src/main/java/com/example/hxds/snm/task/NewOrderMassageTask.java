package com.example.hxds.snm.task;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.snm.entity.NewOrderMessage;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-30 23:51
 **/
@Component
@Slf4j
public class NewOrderMassageTask {

    @Resource
    private ConnectionFactory factory;

    /**
     * 1. 获取Connection、Channel
     * 2. 定义交换机，根据 routingKey 路由消息
     * 3. 定义MQ消息的属性对象
     * 4. 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)并绑定
     * 5. 向交换机发送消息，并附带 routingKey
     *
     * @param list
     */
    public void sendNewOrderMessage(List<NewOrderMessage> list) {
        int ttl = 1 * 60 * 1000;
        String exchangeName = "new_order_private";
        try (
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()
        ) {
            // 定义交换机，根据 routingKey 路由消息
            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
            HashMap param = Maps.newHashMap();
            for (NewOrderMessage message : list) {
                // 定义MQ消息的属性信息
                HashMap map = new HashMap<>() {{
                    put("orderId", message.getOrderId());
                    put("from", message.getFrom());
                    put("to", message.getTo());
                    put("expectsFee", message.getExpectsFee());
                    put("mileage", message.getMileage());
                    put("minute", message.getMinute());
                    put("distance", message.getDistance());
                    put("favourFee", message.getFavourFee());
                }};
                // 定义MQ消息的属性对象
                AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().contentEncoding("UTF-8")
                        .headers(map).expiration(ttl + "").build();
                String queueName = "queue_" + message.getUserId();
                String routingKey = message.getUserId();
                // 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)
                channel.queueDeclare(queueName, true, false, false, param);
                channel.queueBind(queueName, exchangeName, routingKey);
                // 向交换机发送消息，并附带 routingKey
                channel.basicPublish(exchangeName, routingKey, properties, ("新订单" + message.getOrderId()).getBytes());
                log.debug(message.getUserId() + "的新订单消息发送成功");
            }
        } catch (Exception e) {
            log.error("执行异常", e);
            throw new HxdsException("新订单消息发送失败");
        }
    }

    @Async
    public void sendNewOrderMessageAsync(ArrayList<NewOrderMessage> list) {
        this.sendNewOrderMessage(list);
    }

    /**
     * 1. 获取Connection、Channel
     * 2. 定义交换机，根据 routingKey 路由消息
     * 3. 定义队列(持久化缓存消息，消息接收不加锁，消息全部接收完并不删除队列)并绑定
     * 4. 避免一次接收过多消息，采用限流方式
     * 5. while循环接收消息：定义消息属性对象，然后将其封装到NewOrderMessage
     * 6. 确认接收到消息，让 MQ 删除该消息
     * 7. 消息倒叙，让新消息排在前面
     *
     * @param userId
     * @return
     */
    public List<NewOrderMessage> receiveNewOrderMessage(long userId) {
        String exchangeName = "new_order_private";
        String queueName = "queue_" + userId;
        String routingKey = userId + "";
        List<NewOrderMessage> list = Lists.newArrayList();
        try (
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();
        ) {
            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, exchangeName, routingKey);
            // 每次接收10条消息然后循环接收
            channel.basicQos(0, 10, true);
            while (true) {
                GetResponse response = channel.basicGet(queueName, true);
                if (!Objects.isNull(response)) {
                    AMQP.BasicProperties properties = response.getProps();
                    Map<String, Object> map = properties.getHeaders();
                    String orderId = MapUtil.getStr(map, "orderId");
                    String from = MapUtil.getStr(map, "from");
                    String to = MapUtil.getStr(map, "to");
                    String expectsFee = MapUtil.getStr(map, "expectsFee");
                    String mileage = MapUtil.getStr(map, "mileage");
                    String minute = MapUtil.getStr(map, "minute");
                    String distance = MapUtil.getStr(map, "distance");
                    String favourFee = MapUtil.getStr(map, "favourFee");
                    NewOrderMessage message = new NewOrderMessage();
                    message.setOrderId(orderId);
                    message.setFrom(from);
                    message.setTo(to);
                    message.setExpectsFee(expectsFee);
                    message.setMileage(mileage);
                    message.setMinute(minute);
                    message.setDistance(distance);
                    message.setFavourFee(favourFee);
                    list.add(message);
                    byte[] body = response.getBody();
                    String msg = new String(body);
                    log.debug("从RabbitMQ接收的订单消息：" + msg);
                    long deliveryTag = response.getEnvelope().getDeliveryTag();
                    channel.basicAck(deliveryTag, true);
                } else {
                    break;
                }
            }
            ListUtil.reverse(list);
            return list;
        } catch (Exception e) {
            log.error("执行异常", e);
            throw new HxdsException("接收新订单消息失败");
        }
    }

    /**
     * 1. 获取Connection、Channel
     * 2. 定义交换机，根据 routingKey 路由消息
     * 3. 删除队列
     * @param userId
     */
    public void deleteNewOrderQueue(long userId) {
        String exchangeName = "new_order_private"; //交换机名字
        String queueName = "queue_" + userId; //队列名字
        try (
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();
        ) {
            channel.exchangeDeclare(exchangeName,BuiltinExchangeType.DIRECT);
            channel.queueDelete(queueName);
            log.debug(userId + "的新订单消息队列成功删除");
        } catch (Exception e) {
            log.error(userId + "的新订单队列删除失败", e);
            throw new HxdsException("新订单队列删除失败");
        }
    }

    @Async
    public void deleteNewOrderQueueAsync(long userId){
        this.deleteNewOrderQueue(userId);
    }

    /**
     * 1. 获取Connection、Channel
     * 2. 定义交换机，根据 routingKey 路由消息
     * 3. 清空队列
     * @param userId
     */
    public void clearNewOrderQueue(long userId){
        String exchangeName = "new_order_private"; //交换机名字
        String queueName = "queue_" + userId; //队列名字
        try (
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();
        ) {
            channel.exchangeDeclare(exchangeName,BuiltinExchangeType.DIRECT);
            channel.queuePurge(queueName);
            log.debug(userId + "的新订单消息队列清空删除");
        } catch (Exception e) {
            log.error(userId + "的新订单队列清空失败", e);
            throw new HxdsException("新订单队列清空失败");
        }
    }

    @Async
    public void clearNewOrderQueueAsync(long userId){
        this.clearNewOrderQueue(userId);
    }
}
