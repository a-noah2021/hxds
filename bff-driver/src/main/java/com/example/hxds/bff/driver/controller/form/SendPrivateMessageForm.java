package com.example.hxds.bff.driver.controller.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-01 23:23
 **/
@Data
@Schema(description = "同步发送私有消息的表单")
public class SendPrivateMessageForm {

    @NotBlank(message = "receiverIdentity不能为空")
    @Schema(description = "接收人exchange")
    private String receiverIdentity;

    @NotNull(message = "receiverId不能为空")
    @Min(value = 1, message = "receiverId不能小于1")
    @Schema(description = "接收人ID")
    private Long receiverId;

    @Min(value = 1, message = "ttl不能小于1")
    @Schema(description = "消息过期时间")
    private Integer ttl;

    @Min(value = 0, message = "senderId不能小于0")
    @Schema(description = "发送人ID")
    private Long senderId;

    @NotBlank(message = "senderIdentity不能为空")
    @Schema(description = "发送人exchange")
    private String senderIdentity;

    @Schema(description = "发送人头像")
    private String senderPhoto = "http://static-1258386385.cos.ap-beijing.myqcloud.com/img/System.jpg";

    @NotNull(message = "senderName不能为空")
    @Schema(description = "发送人名称")
    private String senderName;

    @NotNull(message = "消息不能为空")
    @Schema(description = "消息内容")
    private String msg;
}