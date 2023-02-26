package com.example.hxds.nebula.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-02-19 19:17
 **/
public interface MonitoringService {

    void monitoring(MultipartFile file, String name, String text);

    int insertOrderMonitoring(long orderId);
}
