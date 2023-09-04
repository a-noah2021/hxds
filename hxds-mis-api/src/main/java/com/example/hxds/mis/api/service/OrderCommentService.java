package com.example.hxds.mis.api.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.mis.api.controller.form.AcceptCommentAppealForm;
import com.example.hxds.mis.api.controller.form.SearchCommentByPageForm;
import com.example.hxds.mis.api.controller.form.HandleCommentAppealForm;
import com.example.hxds.mis.api.controller.form.SearchAppealContentForm;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:30
 **/
public interface OrderCommentService {

    PageUtils searchCommentByPage(SearchCommentByPageForm form);
    
    void acceptCommentAppeal(AcceptCommentAppealForm form);

    void handleCommentAppeal(HandleCommentAppealForm form);

    HashMap searchAppealContent(SearchAppealContentForm form);
}
