package com.example.hxds.snm.service.impl;

import com.example.hxds.snm.db.dao.MessageDao;
import com.example.hxds.snm.db.dao.MessageRefDao;
import com.example.hxds.snm.db.pojo.MessageEntity;
import com.example.hxds.snm.db.pojo.MessageRefEntity;
import com.example.hxds.snm.service.MessageService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 21:58
 **/
@Service
public class MessageServiceImpl implements MessageService {
    @Resource
    private MessageDao messageDao;

    @Resource
    private MessageRefDao messageRefDao;

    @Override
    public String insertMessage(MessageEntity entity) {
        String id = messageDao.insert(entity);
        return id;
    }

    @Override
    public HashMap searchMessageById(String id) {
        HashMap map = messageDao.searchMessageById(id);
        return map;
    }

    @Override
    public String insertRef(MessageRefEntity entity) {
        String id = messageRefDao.insert(entity);
        return id;
    }

    @Override
    public long searchUnreadCount(long userId, String identity) {
        long count = messageRefDao.searchUnreadCount(userId, identity);
        return count;
    }

    @Override
    public long searchLastCount(long userId, String identity) {
        long count = messageRefDao.searchLastCount(userId, identity);
        return count;
    }

    @Override
    public long updateUnreadMessage(String id) {
        long rows = messageRefDao.updateUnreadMessage(id);
        return rows;
    }

    @Override
    public long deleteMessageRefById(String id) {
        long rows = messageRefDao.deleteMessageRefById(id);
        return rows;
    }

    @Override
    public long deleteUserMessageRef(long userId, String identity) {
        long rows = messageRefDao.deleteUserMessageRef(userId, identity);
        return rows;
    }
}


