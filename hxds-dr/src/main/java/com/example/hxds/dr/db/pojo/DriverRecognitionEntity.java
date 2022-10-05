package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class DriverRecognitionEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 检测日期
     */
    private String date;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}