package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.StartCommentWorkflowForm;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
/**
 * @author zhanglumin
 * @since 2023/9/4 3:41 PM
 */
@FeignClient(value = "hxds-workflow")
public interface WorkflowServiceApi {

    @PostMapping("/comment/startCommentWorkflow")
    R startCommentWorkflow(StartCommentWorkflowForm form);
}