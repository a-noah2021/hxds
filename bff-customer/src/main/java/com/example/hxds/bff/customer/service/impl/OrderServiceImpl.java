package com.example.hxds.bff.customer.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.bff.customer.controller.form.*;
import com.example.hxds.bff.customer.feign.*;
import com.example.hxds.bff.customer.service.OrderService;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.R;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPay;
import com.example.hxds.common.wxpay.WXPayUtil;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.example.hxds.common.constants.HxdsConstants.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 00:13
 **/
@Service
@Slf4j
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
    private SnmServiceApi snmServiceApi;

    @Resource
    private DrServiceApi drServiceApi;

    @Resource
    private VhrServiceApi vhrServiceApi;

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
        SearchBefittingDriverAboutOrderForm form_3 = new SearchBefittingDriverAboutOrderForm();
        form_3.setStartPlaceLatitude(startPlaceLatitude);
        form_3.setStartPlaceLongitude(startPlaceLongitude);
        form_3.setEndPlaceLatitude(endPlaceLatitude);
        form_3.setEndPlaceLongitude(endPlaceLongitude);
        form_3.setMileage(mileage);

        r = mpsServiceApi.searchBefittingDriverAboutOrder(form_3);
        ArrayList<HashMap> list = (ArrayList<HashMap>) r.get("result");
        HashMap result = new HashMap() {{
            put(COUNT_MAP_KEY, 0);
        }};

        // 如果存在适合接单的司机就创建订单，否则就不创建订单
        if (list.size() > 0) {
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
            SendNewOrderMessageForm form_5 = new SendNewOrderMessageForm();
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
            result.replace(COUNT_MAP_KEY, list.size());
        }
        return result;
    }

    @Override
    public Integer searchOrderStatus(SearchOrderStatusForm form) {
        R r = odrServiceApi.searchOrderStatus(form);
        Integer result = MapUtil.getInt(r, "result");
        return result;
    }

    @Override
    @Transactional
    @LcnTransaction
    public String deleteUnAcceptOrder(DeleteUnAcceptOrderForm form) {
        R r = odrServiceApi.deleteUnAcceptOrder(form);
        String result = MapUtil.getStr(r, "result");
        return result;
    }

    @Override
    public HashMap hasCustomerCurrentOrder(HasCustomerCurrentOrderForm form) {
        R r = odrServiceApi.hasCustomerCurrentOrder(form);
        HashMap map = (HashMap) r.get("result");
        return map;
    }

    @Override
    public HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form) {
        R r = odrServiceApi.searchOrderForMoveById(form);
        HashMap map = (HashMap) r.get("result");
        return map;
    }

    @Override
    public boolean confirmArriveStartPlace(ConfirmArriveStartPlaceForm form) {
        R r = odrServiceApi.confirmArriveStartPlace(form);
        boolean result = MapUtil.getBool(r, "result");
        return result;
    }

    @Override
    public HashMap searchOrderById(SearchOrderByIdForm form) {
        R r = odrServiceApi.searchOrderById(form);
        HashMap map = (HashMap) r.get("result");
        Long driverId = MapUtil.getLong(map, "driverId");
        if (!Objects.isNull(driverId)) {
            SearchDriverBriefInfoForm infoForm = new SearchDriverBriefInfoForm();
            infoForm.setDriverId(driverId);
            r = drServiceApi.searchDriverBriefInfo(infoForm);
            HashMap temp = (HashMap) r.get("result");
            map.putAll(temp);
            return map;
        }
        return null;
    }

    @Override
    @Transactional
    @LcnTransaction
    public HashMap createWxPayment(long orderId, long customerId, Long customerVoucherId, Long voucherId) {
        // 先查询订单是否为6状态，其余状态都不可以生成支付订单
        ValidCanPayOrderForm form_1 = new ValidCanPayOrderForm();
        form_1.setOrderId(orderId);
        form_1.setCustomerId(customerId);
        R r = odrServiceApi.validCanPayOrder(form_1);
        HashMap map = (HashMap) r.get("result");
        String amount = MapUtil.getStr(map, "realFee");
        String uuid = MapUtil.getStr(map, "uuid");
        long driverId = MapUtil.getLong(map, "driverId");
        String discount = "0.00";
        if (!Objects.isNull(voucherId) && !Objects.isNull(customerVoucherId)) {
            // 查询代金券是否可以使用并绑定
            UseVoucherForm form_2 = new UseVoucherForm();
            form_2.setCustomerId(customerId);
            form_2.setVoucherId(voucherId);
            form_2.setOrderId(orderId);
            form_2.setAmount(amount);
            r = vhrServiceApi.useVoucher(form_2);
            discount = MapUtil.getStr(r, "result");
        }
        if (new BigDecimal(amount).compareTo(new BigDecimal(discount)) == -1) {
            throw new HxdsException("总金额不能小于优惠劵面额");
        }
        // 修改实付金额
        amount = NumberUtil.sub(amount, discount).toString();
        UpdateBillPaymentForm form_3 = new UpdateBillPaymentForm();
        form_3.setOrderId(orderId);
        form_3.setRealPay(amount);
        form_3.setVoucherFee(discount);
        odrServiceApi.updateBillPayment(form_3);
        // 查询用户的OpenId字符串
        SearchCustomerOpenIdForm form_4 = new SearchCustomerOpenIdForm();
        form_4.setCustomerId(customerId);
        r = cstServiceApi.searchCustomerOpenId(form_4);
        String customerOpenId = MapUtil.getStr(r, "result");
        // 查询司机的OpenId字符串
        SearchDriverOpenIdForm form_5 = new SearchDriverOpenIdForm();
        form_5.setDriverId(driverId);
        r = drServiceApi.searchDriverOpenId(form_5);
        String driverOpenId = MapUtil.getStr(r, "result");
        // 创建支付订单
        try {
            WXPay wxPay = new WXPay(myWXPayConfig);
            HashMap param = Maps.newHashMap();
            // 生成随机字符串
            param.put("nonce_str", WXPayUtil.generateNonceStr());
            param.put("body", "代驾费");
            param.put("out_trade_no", uuid);
            // 充值金额转换成分为单位，并且让BigDecimal取整数
            // amount = "1.00"
            param.put("total_fee", NumberUtil.mul(amount, "100").setScale(0, RoundingMode.FLOOR).toString());
            param.put("total_fee", "4");
            param.put("spbill_create_ip", "127.0.0.1");
            // 这里要修改成内网穿透的公网URL
            param.put("notify_url", "http://s2.nsloop.com:8955/hxds-odr/order/recieveMessage");
            param.put("trade_type", "JSAPI");
            param.put("openid", customerOpenId);
            param.put("attach", driverOpenId);
            // 支付需要分账
            param.put("profit_sharing", "Y");
            // 创建支付订单
            Map<String, String> result = wxPay.unifiedOrder(param);
            System.out.println(result);
            // 预支付交易会话标识id
            String prepayId = result.get("prepay_id");
            if (Objects.isNull(prepayId)) {
                // 更新订单记录中的prepay_id字段值
                UpdateOrderPrepayIdForm form_6 = new UpdateOrderPrepayIdForm();
                form_6.setOrderId(orderId);
                form_6.setPrepayId(prepayId);
                odrServiceApi.updateOrderPrepayId(form_6);
                // 准备生成数字签名用的数据
                map.clear();
                map.put("appId", myWXPayConfig.getAppID());
                String timeStamp = new Date().getTime() + "";
                map.put("timeStamp", timeStamp);
                String nonceStr = WXPayUtil.generateNonceStr();
                map.put("nonceStr", nonceStr);
                map.put("package", "prepay_id=" + prepayId);
                map.put("signType", "MD5");
                // 生成数字签名
                String paySign = WXPayUtil.generateSignature(map, myWXPayConfig.getKey());
                // 清理HashMap，放入结果
                map.clear();
                map.put("package", "prepay_id=" + prepayId);
                map.put("timeStamp", timeStamp);
                map.put("nonceStr", nonceStr);
                map.put("paySign", paySign);
                // uuid用户付款成功后，移动端主动请求更新充值状态
                map.put("uuid", uuid);
                return map;
            } else {
                log.error("创建支付订单失败");
                throw new HxdsException("创建支付订单失败");
            }
        } catch (Exception e) {
            log.error("创建支付订单失败", e);
            throw new HxdsException("创建支付订单失败");
        }
    }

    @Override
    @Transactional
    @LcnTransaction
    public String updateOrderAboutPayment(UpdateOrderAboutPaymentForm form) {
        R r = odrServiceApi.updateOrderAboutPayment(form);
        String result = MapUtil.getStr(r, "result");
        return result;
    }

}
