package com.example.hxds.mis.api.feign;

import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.AcceptCommentAppealForm;
import com.example.hxds.mis.api.controller.form.HandleCommentAppealForm;
import com.example.hxds.mis.api.controller.form.SearchAppealContentForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-04 21:28
 **/
@FeignClient(value = "hxds-workflow")
public interface WorkflowServiceApi {

    @PostMapping("/comment/acceptCommentAppeal")
    R acceptCommentAppeal(AcceptCommentAppealForm form);

    @PostMapping("/comment/handleCommentAppeal")
    R handleCommentAppeal(HandleCommentAppealForm form);

    @PostMapping("/comment/searchAppealContent")
    R searchAppealContent(SearchAppealContentForm form);

}
