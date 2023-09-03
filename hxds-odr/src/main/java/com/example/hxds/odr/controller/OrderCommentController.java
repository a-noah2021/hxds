package com.example.hxds.odr.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.R;
import com.example.hxds.odr.controller.form.InsertCommentForm;
import com.example.hxds.odr.db.pojo.OrderCommentEntity;
import com.example.hxds.odr.service.OrderCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Date;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 00:57
 **/
@RestController
@RequestMapping("/comment")
@Tag(name = "OrderCommentController", description = "订单评价模块Web接口")
public class OrderCommentController {

    @Resource
    private OrderCommentService orderCommentService;

    @PostMapping("/insertComment")
    @Operation(summary = "保存订单评价")
    public R insertComment(@RequestBody @Valid InsertCommentForm form) {
        OrderCommentEntity entity = BeanUtil.toBean(form, OrderCommentEntity.class);
        entity.setStatus((byte) 1);
        entity.setCreateTime(new Date());
        int rows = orderCommentService.insert(entity);
        return R.ok().put("rows", rows);
    }
}