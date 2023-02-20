package com.example.hxds.nebula.service.impl;

import cn.hutool.core.util.IdUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.nebula.db.dao.OrderMonitoringDao;
import com.example.hxds.nebula.db.dao.OrderVoiceTextDao;
import com.example.hxds.nebula.db.pojo.OrderVoiceTextEntity;
import com.example.hxds.nebula.service.MonitoringService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 19:18
 **/
@Service
@Slf4j
public class MonitoringServiceImpl implements MonitoringService {
    @Resource
    private OrderVoiceTextDao orderVoiceTextDao;

    @Resource
    private OrderMonitoringDao orderMonitoringDao;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Override
    @Transactional
    public void monitoring(MultipartFile file, String name, String text) {
        // 把录音文件上传到minio
        try {
            MinioClient client = new MinioClient.Builder().endpoint(endpoint)
                    .credentials(accessKey, secretKey).build();
            client.putObject(PutObjectArgs.builder().bucket("hxds-record")
                    .object(name).stream(file.getInputStream(), -1, 20971520)
                    .contentType("audio/x-mpeg").build());
        } catch (Exception e) {
            log.error("上传代驾录音文件失败", e);
            throw new HxdsException("上传代驾录音文件失败");
        }
        OrderVoiceTextEntity entity = new OrderVoiceTextEntity();
        // 文件名格式例如:2156356656617-1.mp3，解析出订单号
        String[] temp = name.substring(0, name.indexOf(".mp3")).split("-");
        Long orderId = Long.parseLong(temp[0]);
        String uuid = IdUtil.simpleUUID();
        entity.setOrderId(orderId);
        entity.setUuid(uuid);
        entity.setRecordFile(name);
        entity.setText(text);
        // 把文稿保持到HBase
        int rows = orderVoiceTextDao.insert(entity);
        if (rows != 1) {
            throw new HxdsException("保存录音文稿失败");
        }
        // TODO:执行文稿内容审查
    }
}
