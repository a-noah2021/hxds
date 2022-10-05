package com.example.hxds.mis.api.db.pojo;

import lombok.Data;

import java.io.Serializable;

/**
 * 
 * @TableName tb_feedback
 */
@Data
public class FeedbackEntity implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 反馈者姓名
     */
    private String customerName;

    /**
     * 反馈者电话
     */
    private String customerTel;

    /**
     * 接待员ID
     */
    private Long userId;

    /**
     * 反馈类型，1系统故障，2服务质量，3支付异常，4其他
     */
    private Integer type;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 反馈状态，1未处理，2已处理
     */
    private Integer status;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}