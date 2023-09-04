package com.example.hxds.mis.api.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.AcceptCommentAppealForm;
import com.example.hxds.mis.api.controller.form.HandleCommentAppealForm;
import com.example.hxds.mis.api.controller.form.SearchAppealContentForm;
import com.example.hxds.mis.api.db.dao.UserDao;
import com.example.hxds.mis.api.feign.OdrServiceApi;
import com.example.hxds.mis.api.feign.WorkflowServiceApi;
import com.example.hxds.mis.api.service.OrderCommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:31
 **/
@Service
public class OrderCommentServiceImpl implements OrderCommentService {

    @Resource
    private UserDao userDao;

    @Resource
    private WorkflowServiceApi workflowServiceApi;

    @Resource
    private OdrServiceApi odrServiceApi;

    @Override
    public PageUtils searchCommentByPage(SearchCommentByPageForm form) {
        R r = odrServiceApi.searchCommentByPage(form);
        HashMap map = (HashMap) r.get("result");
        PageUtils pageUtils = BeanUtil.toBean(map, PageUtils.class);
        return pageUtils;
    }

    @Override
    @Transactional
    @LcnTransaction
    public void acceptCommentAppeal(AcceptCommentAppealForm form) {
        HashMap map = userDao.searchUserSummary(form.getUserId());
        String name = MapUtil.getStr(map, "name");
        form.setUserName(name);
        workflowServiceApi.acceptCommentAppeal(form);
    }

    @Override
    @Transactional
    @LcnTransaction
    public void handleCommentAppeal(HandleCommentAppealForm form) {
        workflowServiceApi.handleCommentAppeal(form);
    }

    @Override
    public HashMap searchAppealContent(SearchAppealContentForm form) {
        R r = workflowServiceApi.searchAppealContent(form);
        HashMap map = (HashMap) r.get("result");
        return map;
    }

}
