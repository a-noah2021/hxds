package com.example.hxds.nebula.db.pojo;

import lombok.Data;

@Data
public class OrderVoiceTextEntity {
    private Long id;
    private String uuid;
    private Long orderId;
    private String recordFile;
    private String text;
    private String label;
    private String suggestion;
    private String keywords;
    private String createTime;
}