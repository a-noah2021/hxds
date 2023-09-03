package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.InsertCommentForm;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 01:05
 **/
public interface OrderCommentService {

    int insertComment(InsertCommentForm form);

}
