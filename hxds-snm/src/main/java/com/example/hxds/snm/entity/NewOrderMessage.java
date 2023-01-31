package com.example.hxds.snm.entity;

import lombok.Data;

/**
 * @program: hxds-snm
 * @description:
 * @author: noah2021
 * @date: 2023-01-30 23:35
 **/
@Data
public class NewOrderMessage {
    private String userId;
    private String orderId;
    private String from;
    private String to;
    private String expectsFee;
    private String mileage;
    private String minute;
    private String distance;
    private String favourFee;
}
