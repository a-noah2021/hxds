package com.example.hxds.nebula.task;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.nebula.db.dao.OrderMonitoringDao;
import com.example.hxds.nebula.db.dao.OrderVoiceTextDao;
import com.example.hxds.nebula.db.pojo.OrderMonitoringEntity;
import com.google.common.collect.Lists;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.ciModel.auditing.*;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-03-04 20:22
 **/
@Component
@Slf4j
public class VoiceTextCheckTask {
    @Value("${tencent.cloud.appId}")
    private String appId;

    @Value("${tencent.cloud.secretId}")
    private String secretId;

    @Value("${tencent.cloud.secretKey}")
    private String secretKey;

    @Value("${tencent.cloud.bucket-public}")
    private String bucketPublic;

    @Resource
    private OrderVoiceTextDao orderVoiceTextDao;

    @Resource
    private OrderMonitoringDao orderMonitoringDao;

    @Async
    @Transactional
    public void checkText(long orderId, String content, String uuid) {
        String label = "Normal"; // 审核结果
        String suggestion = "Pass"; // 后续建议

        // 后续建议模板
        Map<String, String> template = new HashMap<>() {{
            put("0", "Pass");
            put("1", "Block");
            put("2", "Review");
        }};
        if (StrUtil.isNotBlank(content)) {
            BasicCOSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
            Region region = new Region("ap-beijing");
            ClientConfig config = new ClientConfig(region);
            COSClient client = new COSClient(credentials, config);
            TextAuditingRequest request = new TextAuditingRequest();
            request.setBucketName(bucketPublic);
            request.getInput().setContent(Base64.encode(content));
            request.getConf().setDetectType("all");
            TextAuditingResponse response = client.createAuditingTextJobs(request);
            AuditingJobsDetail detail = response.getJobsDetail();
            String state = detail.getState();
            ArrayList keywords = Lists.newArrayList();
            if ("Success".equals(state)) {
                label = detail.getLabel(); // 审核结果
                String result = detail.getResult(); // 检测结果
                suggestion = template.get(result); // 后续建议
                List<SectionInfo> list = detail.getSectionList();
                for (SectionInfo info : list) {
                    String keywords1 = info.getPornInfo().getKeywords();
                    String keywords2 = info.getIllegalInfo().getKeywords();
                    String keywords3 = info.getAbuseInfo().getKeywords();
                    if (keywords1.length() > 0) {
                        List<String> temp = Arrays.asList(keywords1.split(","));
                        keywords.addAll(temp);
                    } else if (keywords2.length() > 0) {
                        List<String> temp = Arrays.asList(keywords2.split(","));
                        keywords.addAll(temp);
                    } else if (keywords3.length() > 0) {
                        List<String> temp = Arrays.asList(keywords3.split(","));
                        keywords.addAll(temp);
                    }
                }
            }
            Long id = orderVoiceTextDao.searchIdByUuid(uuid);
            if (id == null) {
                throw new HxdsException("没有找到代驾语音文本记录");
            }
            HashMap param = new HashMap();
            param.put("id", id);
            param.put("label", label);
            param.put("suggestion", suggestion);
            param.put("keywords", ArrayUtil.join(keywords.toArray(), ","));
            int rows = orderVoiceTextDao.updateCheckResult(param);
            if (rows != 1) {
                throw new HxdsException("更新内容检查结果失败");
            }
            // 查询该订单中有多少个审核录音和需要人工审核的文本
            HashMap map = orderMonitoringDao.searchOrderRecordsAndReviews(orderId);
            id = MapUtil.getLong(map, "id");
            Integer records = MapUtil.getInt(map, "records");
            Integer reviews = MapUtil.getInt(map, "reviews");
            OrderMonitoringEntity entity = new OrderMonitoringEntity();
            entity.setId(id);
            entity.setOrderId(orderId);
            entity.setRecords(records + 1);
            if (suggestion.equals("Review")) {
                entity.setReviews(reviews + 1);
            }
            if (suggestion.equals("Block")) {
                entity.setSafety("danger");
            }
            // 更新 order_monitoring 表中的记录
            rows = orderMonitoringDao.updateOrderMonitoring(entity);
            if (rows != 1) {
                throw new HxdsException("更新订单监控记录失败");
            }
        }
    }
}
