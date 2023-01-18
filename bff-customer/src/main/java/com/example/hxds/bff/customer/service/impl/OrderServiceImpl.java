package com.example.hxds.bff.customer.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.bff.customer.controller.form.CreateNewOrderForm;
import com.example.hxds.bff.customer.controller.form.EstimateOrderChargeForm;
import com.example.hxds.bff.customer.controller.form.EstimateOrderMileageAndMinuteForm;
import com.example.hxds.bff.customer.controller.form.InsertOrderForm;
import com.example.hxds.bff.customer.feign.CstServiceApi;
import com.example.hxds.bff.customer.feign.MpsServiceApi;
import com.example.hxds.bff.customer.feign.OdrServiceApi;
import com.example.hxds.bff.customer.feign.RuleServiceApi;
import com.example.hxds.bff.customer.service.OrderService;
import com.example.hxds.common.util.R;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.hxds.common.constants.HxdsConstants.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 00:13
 **/
@Service
public class OrderServiceImpl implements OrderService {

    private static final String DRIVING_MODE = "driving";

    private static final String MILEAGE_MAP_KEY = "mileage";

    private static final String MINUTE_MAP_KEY = "minute";
    private static final String AMOUNT_MAP_KEY = "amount";
    private static final String RULE_ID_MAP_KEY = "chargeRuleId";
    private static final String BASE_MILEAGE_MAP_KEY = "baseMileage";
    private static final String BASE_MILEAGE_PRICE_MAP_KEY = "baseMileagePrice";
    private static final String EXCEED_MILEAGE_PRICE_MAP_KEY = "exceedMileagePrice";
    private static final String BASE_MINUTE_PRICE_MAP_KEY = "baseMinute";
    private static final String EXCEED_MINUTE_PRICE_MAP_KEY = "exceedMinutePrice";
    private static final String BASE_RETURN_MILEAGE_MAP_KEY = "baseReturnMileage";
    private static final String EXCEED_RETURN_MILEAGE_MAP_KEY = "exceedReturnPrice";
    private static final String COUNT_MAP_KEY = "count";
    private static final String ORDER_ID_MAP_KEY = "orderId";

    @Resource
    private MpsServiceApi mpsServiceApi;

    @Resource
    private RuleServiceApi ruleServiceApi;

    @Resource
    private OdrServiceApi odrServiceApi;

    @Resource
    private CstServiceApi cstServiceApi;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Override
    @Transactional
    @LcnTransaction
    public HashMap createNewOrder(CreateNewOrderForm form) {
        Long customerId = form.getCustomerId();
        String startPlace = form.getStartPlace();
        String startPlaceLatitude = form.getStartPlaceLatitude();
        String startPlaceLongitude = form.getStartPlaceLongitude();
        String endPlace = form.getEndPlace();
        String endPlaceLatitude = form.getEndPlaceLatitude();
        String endPlaceLongitude = form.getEndPlaceLongitude();
        String favourFee = form.getFavourFee();
        // 虽然下单前前端计算好了路线和时长，但是当用户在前端停留过长时，该状态就会发生变化，此时需要重新预估
        // 重新预估里程和事件
        EstimateOrderMileageAndMinuteForm reEstimate2MForm = new EstimateOrderMileageAndMinuteForm();
        reEstimate2MForm.setMode(DRIVING_MODE);
        BeanUtils.copyProperties(form, reEstimate2MForm);
        R r = mpsServiceApi.estimateOrderMileageAndMinute(reEstimate2MForm);
        HashMap map = (HashMap) r.get(RESULT_MAP_KEY);
        String mileage = MapUtil.getStr(map, MILEAGE_MAP_KEY);
        Integer minute = MapUtil.getInt(map, MINUTE_MAP_KEY);
        // 重新预估价钱
        EstimateOrderChargeForm reEstimateChargeForm = new EstimateOrderChargeForm();
        reEstimateChargeForm.setMileage(mileage);
        reEstimateChargeForm.setTime(new DateTime().toTimeStr());
        r = ruleServiceApi.estimateOrderCharge(reEstimateChargeForm);
        map = (HashMap) r.get(RESULT_MAP_KEY);
        String expectsFee = MapUtil.getStr(map, AMOUNT_MAP_KEY);
        String chargeRuleId = MapUtil.getStr(map, RULE_ID_MAP_KEY);
        short baseMileage = MapUtil.getShort(map, BASE_MILEAGE_MAP_KEY);
        String baseMileagePrice = MapUtil.getStr(map, BASE_MILEAGE_PRICE_MAP_KEY);
        String exceedMileagePrice = MapUtil.getStr(map, EXCEED_MILEAGE_PRICE_MAP_KEY);
        short baseMinute = MapUtil.getShort(map, BASE_MINUTE_PRICE_MAP_KEY);
        String exceedMinutePrice = MapUtil.getStr(map, EXCEED_MINUTE_PRICE_MAP_KEY);
        short baseReturnMileage = MapUtil.getShort(map, BASE_RETURN_MILEAGE_MAP_KEY);
        String exceedReturnPrice = MapUtil.getStr(map, EXCEED_RETURN_MILEAGE_MAP_KEY);
        // 搜索适合接单的司机
        /*SearchBefittingDriverAboutOrderForm form_3 = new SearchBefittingDriverAboutOrderForm();
        form_3.setStartPlaceLatitude(startPlaceLatitude);
        form_3.setStartPlaceLongitude(startPlaceLongitude);
        form_3.setEndPlaceLatitude(endPlaceLatitude);
        form_3.setEndPlaceLongitude(endPlaceLongitude);
        form_3.setMileage(mileage);

        r = mpsServiceApi.searchBefittingDriverAboutOrder(form_3);
        ArrayList<HashMap> list = (ArrayList<HashMap>) r.get("result");*/
        HashMap result = new HashMap() {{
            put(COUNT_MAP_KEY, 0);
        }};

        // 如果存在适合接单的司机就创建订单，否则就不创建订单
//        if (list.size() > 0) {
            InsertOrderForm form_4 = new InsertOrderForm();
            //UUID字符串，充当订单号，微信支付时候会用上
            form_4.setUuid(IdUtil.simpleUUID());
            form_4.setCustomerId(customerId);
            form_4.setStartPlace(startPlace);
            form_4.setStartPlaceLatitude(startPlaceLatitude);
            form_4.setStartPlaceLongitude(startPlaceLongitude);
            form_4.setEndPlace(endPlace);
            form_4.setEndPlaceLatitude(endPlaceLatitude);
            form_4.setEndPlaceLongitude(endPlaceLongitude);
            form_4.setExpectsMileage(mileage);
            form_4.setExpectsFee(expectsFee);
            form_4.setFavourFee(favourFee);
            form_4.setDate(new DateTime().toDateStr());
            form_4.setChargeRuleId(Long.parseLong(chargeRuleId));
            form_4.setCarPlate(form.getCarPlate());
            form_4.setCarType(form.getCarType());
            form_4.setBaseMileage(baseMileage);
            form_4.setBaseMileagePrice(baseMileagePrice);
            form_4.setExceedMileagePrice(exceedMileagePrice);
            form_4.setBaseMinute(baseMinute);
            form_4.setExceedMinutePrice(exceedMinutePrice);
            form_4.setBaseReturnMileage(baseReturnMileage);
            form_4.setExceedReturnPrice(exceedReturnPrice);

            r = odrServiceApi.insertOrder(form_4);
            String orderId = MapUtil.getStr(r, RESULT_MAP_KEY);

            // 发送通知给符合条件的司机抢单
            /*SendNewOrderMessageForm form_5 = new SendNewOrderMessageForm();
            String[] driverContent = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                HashMap one = list.get(i);
                String driverId = MapUtil.getStr(one, "driverId");
                String distance = MapUtil.getStr(one, "distance");
                distance = new BigDecimal(distance).setScale(1, RoundingMode.CEILING).toString();
                driverContent[i] = driverId + "#" + distance;
            }
            form_5.setDriversContent(driverContent);
            form_5.setDriversContent(driverContent);
            form_5.setOrderId(Long.parseLong(orderId));
            form_5.setFrom(startPlace);
            form_5.setTo(endPlace);
            form_5.setExpectsFee(expectsFee);
            //里程转化成保留小数点后一位
            mileage = new BigDecimal(mileage).setScale(1, RoundingMode.CEILING).toString();
            form_5.setMileage(mileage);
            form_5.setMinute(minute);
            form_5.setFavourFee(favourFee);

            snmServiceApi.sendNewOrderMessageAsync(form_5);

            result.put(ORDER_ID_MAP_KEY, orderId);
            result.replace(COUNT_MAP_KEY, list.size());*/
//        }
        return result;
    }
}
