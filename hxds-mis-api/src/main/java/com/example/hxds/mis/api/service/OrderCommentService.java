package com.example.hxds.mis.api.service;

import com.example.hxds.common.util.PageUtils;
import com.example.hxds.mis.api.controller.form.SearchCommentByPageForm;

/**
 * @author zhanglumin
 * @since 2023/9/4 4:38 PM
 */
public interface OrderCommentService {

    PageUtils searchCommentByPage(SearchCommentByPageForm form);

}
