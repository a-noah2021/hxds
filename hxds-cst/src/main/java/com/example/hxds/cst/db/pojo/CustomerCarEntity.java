package com.example.hxds.cst.db.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerCarEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 车牌号
     */
    private String carPlate;

    /**
     * 车型
     */
    private String carType;

    private static final long serialVersionUID = 1L;
}