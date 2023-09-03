package com.example.hxds.odr.quartz.job;

import cn.hutool.core.map.MapUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPay;
import com.example.hxds.common.wxpay.WXPayConstants;
import com.example.hxds.common.wxpay.WXPayUtil;
import com.example.hxds.odr.db.dao.OrderProfitsharingDao;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-09-03 21:44
 **/
@Slf4j
public class SearchProfitsharingJob extends QuartzJobBean {
    @Resource
    private OrderProfitsharingDao profitsharingDao;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Map map = context.getJobDetail().getJobDataMap();
        String uuid = MapUtil.getStr(map, "uuid");
        long profitsharingId = MapUtil.getLong(map, "profitsharingId");
        String payId = MapUtil.getStr(map, "payId");
        try {
            WXPay wxPay = new WXPay(myWXPayConfig);
            String url = "/pay/profitsharingquery";
            HashMap param = new HashMap() {{
                put("mch_id", myWXPayConfig.getMchID());
                put("transaction_id", payId);
                put("out_order_no", uuid);
                put("nonce_str", WXPayUtil.generateNonceStr());
            }};
            // 生成数字签名
            String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256);
            param.put("sign", sign);
            // 查询分账结果
            String response = wxPay.requestWithCert(url, param, 3000, 3000);
            log.debug(response);
            // 验证响应的数字签名
            if (WXPayUtil.isSignatureValid(response, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256)) {
                Map<String, String> data = wxPay.processResponseXml(response, WXPayConstants.SignType.HMACSHA256);
                String returnCode = data.get("return_code");
                String resultCode = data.get("result_code");
                if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
                    String status = data.get("status");
                    if ("FINISHED".equals(status)) {
                        // 把分账记录更改为2状态
                        int rows = profitsharingDao.updateProfitsharingStatus(profitsharingId);
                        if (rows != 1) {
                            log.error("更新分账状态失败", new HxdsException("更新分账状态失败"));
                        }
                    }
                } else {
                    log.error("查询分账失败", new HxdsException("查询分账失败"));
                }
            } else {
                log.error("验证数字签名失败", new HxdsException("验证数字签名失败"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
