package com.example.hxds.dr.db.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class DriverSettingsEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 司机ID
     */
    private Long driverId;

    /**
     * 个人设置
     */
    private String settings;

    private static final long serialVersionUID = 1L;
}