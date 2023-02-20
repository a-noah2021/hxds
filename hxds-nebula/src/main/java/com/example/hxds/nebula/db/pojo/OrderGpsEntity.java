package com.example.hxds.nebula.db.pojo;

import lombok.Data;

@Data
public class OrderGpsEntity {
    private Long id;
    private Long orderId;
    private Long driverId;
    private Long customerId;
    private String latitude;
    private String longitude;
    private String speed;
    private String createTime;
}