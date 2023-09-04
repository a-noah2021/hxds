package com.example.hxds.mis.api.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.mis.api.controller.form.SearchCommentByPageForm;
import com.example.hxds.mis.api.feign.OdrServiceApi;
import com.example.hxds.mis.api.service.OrderCommentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
/**
 * @author zhanglumin
 * @since 2023/9/4 4:38 PM
 */
@Service
public class OrderCommentServiceImpl implements OrderCommentService {

    @Resource
    private OdrServiceApi odrServiceApi;

    @Override
    public PageUtils searchCommentByPage(SearchCommentByPageForm form) {
        R r = odrServiceApi.searchCommentByPage(form);
        HashMap map = (HashMap) r.get("result");
        PageUtils pageUtils = BeanUtil.toBean(map, PageUtils.class);
        return pageUtils;
    }
}
