package com.example.hxds.snm.service;

import com.example.hxds.snm.db.pojo.MessageEntity;
import com.example.hxds.snm.db.pojo.MessageRefEntity;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 21:56
 **/
public interface MessageService {
    String insertMessage(MessageEntity entity);

    HashMap searchMessageById(String id);

    String insertRef(MessageRefEntity entity);

    long searchUnreadCount(long userId, String identity);

    long searchLastCount(long userId, String identity);

    long updateUnreadMessage(String id);

    long deleteMessageRefById(String id);

    long deleteUserMessageRef(long userId, String identity);
}
