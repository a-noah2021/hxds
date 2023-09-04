package com.example.hxds.bff.driver.service.impl;

import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.bff.driver.controller.form.StartCommentWorkflowForm;
import com.example.hxds.bff.driver.feign.WorkflowServiceApi;
import com.example.hxds.bff.driver.service.OrderCommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
/**
 * @author zhanglumin
 * @since 2023/9/4 3:43 PM
 */
@Service
public class OrderCommentServiceImpl implements OrderCommentService {
    @Resource
    private WorkflowServiceApi workflowServiceApi;

    @Override
    @Transactional
    @LcnTransaction
    public void startCommentWorkflow(StartCommentWorkflowForm form) {
        workflowServiceApi.startCommentWorkflow(form);
    }

}
