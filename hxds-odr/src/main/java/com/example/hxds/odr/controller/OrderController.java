package com.example.hxds.odr.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.util.R;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPayUtil;
import com.example.hxds.odr.controller.form.*;
import com.example.hxds.odr.db.pojo.OrderBillEntity;
import com.example.hxds.odr.db.pojo.OrderEntity;
import com.example.hxds.odr.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.hxds.common.constants.HxdsConstants.*;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-31 22:00
 **/
@RestController
@RequestMapping("/order")
@Tag(name = "OrderController", description = "订单模块Web接口")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @PostMapping("/searchDriverTodayBusinessData")
    @Operation(summary = "查询司机当天营业数据")
    public R searchDriverTodayBusinessData(@RequestBody @Valid SearchDriverTodayBusinessDataForm form) {
        HashMap result = orderService.searchDriverTodayBusinessData(form.getDriverId());
        return R.ok().put(RESULT_MAP_KEY, result);
    }

    @PostMapping("/insertOrder")
    @Operation(summary = "顾客下单")
    public R insertOrder(@RequestBody @Valid InsertOrderForm form) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setUuid(form.getUuid());
        orderEntity.setCustomerId(form.getCustomerId());
        orderEntity.setStartPlace(form.getStartPlace());
        JSONObject json = new JSONObject();
        json.set(LATITUDE, form.getStartPlaceLatitude());
        json.set(LONGITUDE, form.getStartPlaceLongitude());
        orderEntity.setStartPlaceLocation(json.toString());
        orderEntity.setEndPlace(form.getEndPlace());
        json = new JSONObject();
        json.set(LATITUDE, form.getEndPlaceLatitude());
        json.set(LONGITUDE, form.getEndPlaceLongitude());
        orderEntity.setEndPlaceLocation(json.toString());
        orderEntity.setExpectsMileage(new BigDecimal(form.getExpectsMileage()));
        orderEntity.setExpectsFee(new BigDecimal(form.getExpectsFee()));
        orderEntity.setFavourFee(new BigDecimal(form.getFavourFee()));
        orderEntity.setChargeRuleId(form.getChargeRuleId());
        orderEntity.setCarPlate(form.getCarPlate());
        orderEntity.setCarType(form.getCarType());
        orderEntity.setDate(form.getDate());

        OrderBillEntity orderBillEntity = new OrderBillEntity();
        orderBillEntity.setBaseMileage(form.getBaseMileage());
        orderBillEntity.setBaseMileagePrice(new BigDecimal(form.getBaseMileagePrice()));
        orderBillEntity.setExceedMileagePrice(new BigDecimal(form.getExceedMileagePrice()));
        orderBillEntity.setBaseMinute(form.getBaseMinute());
        orderBillEntity.setExceedMinutePrice(new BigDecimal(form.getExceedMinutePrice()));
        orderBillEntity.setBaseReturnMileage(form.getBaseReturnMileage());
        orderBillEntity.setExceedReturnPrice(new BigDecimal(form.getExceedReturnPrice()));

        String id = orderService.insertOrder(orderEntity, orderBillEntity);
        return R.ok().put(RESULT_MAP_KEY, id);
    }

    @PostMapping("/acceptNewOrder")
    @Operation(summary = "司机接单")
    public R acceptNewOrder(@RequestBody @Valid AcceptNewOrderForm form) {
        String result = orderService.acceptNewOrder(form.getDriverId(), form.getOrderId());
        return R.ok().put("result", result);
    }

    @PostMapping("/searchDriverExecuteOrder")
    @Operation(summary = "查询司机正在执行的订单记录")
    public R searchDriveExecutorOrder(@RequestBody @Valid SearchDriverExecuteOrderForm form) {
        Map param = BeanUtil.beanToMap(form);
        HashMap map = orderService.searchDriverExecuteOrder(param);
        return R.ok().put("result", map);
    }

    @PostMapping("/searchOrderStatus")
    @Operation(summary = "查询订单状态")
    public R searchOrderStatus(@RequestBody @Valid SearchOrderStatusForm form) {
        Map param = BeanUtil.beanToMap(form);
        Integer status = orderService.searchOrderStatus(param);
        return R.ok().put("result", status);
    }

    @PostMapping("/deleteUnAcceptOrder")
    @Operation(summary = "删除没有司机接单的订单")
    public R deleteUnAcceptOrder(@RequestBody @Valid DeleteUnAcceptOrderForm form) {
        Map param = BeanUtil.beanToMap(form);
        String result = orderService.deleteUnAcceptOrder(param);
        return R.ok().put("result", result);
    }

    @PostMapping("/searchDriverCurrentOrder")
    @Operation(summary = "查询司机当前订单")
    public R searchDriverCurrentOrder(@RequestBody @Valid SearchDriverCurrentOrderForm form) {
        HashMap map = orderService.searchDriverCurrentOrder(form.getDriverId());
        return R.ok().put("result", map);
    }

    @PostMapping("/hasCustomerCurrentOrder")
    @Operation(summary = "查询乘客是否存在当前的订单")
    public R hasCustomerCurrentOrder(@RequestBody @Valid HasCustomerCurrentOrderForm form) {
        HashMap map = orderService.hasCustomerCurrentOrder(form.getCustomerId());
        return R.ok().put("result", map);
    }

    @PostMapping("/searchOrderForMoveById")
    @Operation(summary = "查询订单信息用于司乘同显功能")
    public R searchOrderForMoveById(@RequestBody @Valid SearchOrderForMoveByIdForm form) {
        Map param = BeanUtil.beanToMap(form);
        HashMap map = orderService.searchOrderForMoveById(param);
        return R.ok().put("result", map);
    }

    @PostMapping("/arriveStartPlace")
    @Operation(summary = "司机到达上车点")
    public R arriveStartPlace(@RequestBody @Valid ArriveStartPlaceForm form) {
        Map param = BeanUtil.beanToMap(form);
        param.put("status", 3);
        int rows = orderService.arriveStartPlace(param);
        return R.ok().put("rows", rows);
    }

    @PostMapping("/confirmArriveStartPlace")
    @Operation(summary = "乘客确认司机到达上车点")
    public R confirmArriveStartPlace(@RequestBody @Valid ConfirmArriveStartPlaceForm form) {
        boolean result = orderService.confirmArriveStartPlace(form.getOrderId());
        return R.ok().put("result", result);
    }

    @PostMapping("/startDriving")
    @Operation(summary = "开始代驾")
    public R startDriving(@RequestBody @Valid StartDrivingForm form) {
        Map param = BeanUtil.beanToMap(form);
        param.put("status", 4);
        int rows = orderService.startDriving(param);
        return R.ok().put("rows", rows);
    }

    @PostMapping("/updateOrderStatus")
    @Operation(summary = "更新订单状态")
    public R updateOrderStatus(@RequestBody @Valid UpdateOrderStatusForm form) {
        Map param = BeanUtil.beanToMap(form);
        int rows = orderService.updateOrderStatus(param);
        return R.ok().put("rows", rows);
    }

    @PostMapping("/searchOrderByPage")
    @Operation(summary = "查询订单分页记录")
    public R searchOrderByPage(@RequestBody @Valid SearchOrderByPageForm form) {
        Map param = BeanUtil.beanToMap(form);
        int page = form.getPage();
        int length = form.getLength();
        int start = (page - 1) * length;
        param.put("start", start);
        PageUtils pageUtils = orderService.searchOrderByPage(param);
        return R.ok().put("result", pageUtils);
    }

    @PostMapping("/searchOrderContent")
    @Operation(summary = "查询订单详情")
    public R searchOrderContent(@RequestBody @Valid SearchOrderContentForm form) {
        HashMap map = orderService.searchOrderContent(form.getOrderId());
        return R.ok().put("result", map);
    }

    @PostMapping("/searchOrderStartLocationIn30Days")
    @Operation(summary = "查询30天以内订单上车定点位")
    public R searchOrderStartLocationIn30Days() {
        List<Map> result = orderService.searchOrderStartLocationIn30Days();
        return R.ok().put("result", result);
    }

    @PostMapping("/validDriverOwnOrder")
    @Operation(summary = "查询司机是否关联某订单")
    public R validDriverOwnOrder(@RequestBody @Valid ValidDriverOwnOrderForm form) {
        Map param = BeanUtil.beanToMap(form);
        boolean bool = orderService.validDriverOwnOrder(param);
        return R.ok().put("result", bool);
    }

    @PostMapping("/searchSettlementNeedData")
    @Operation(summary = "查询订单的开始和等时")
    public R searchSettlementNeedData(@RequestBody @Valid SearchSettlementNeedDataForm form) {
        Map map = orderService.searchSettlementNeedData(form.getOrderId());
        return R.ok().put("result", map);
    }

    @PostMapping("/searchOrderById")
    @Operation(summary = "根据id查询订单信息")
    public R searchOrderById(@RequestBody @Valid SearchOrderByIdForm form) {
        Map param = BeanUtil.beanToMap(form);
        Map map = orderService.searchOrderById(param);
        return R.ok().put("result", map);
    }

    @PostMapping("/validCanPayOrder")
    @Operation(summary = "检查订单是否可以支付")
    public R validCanPayOrder(@RequestBody @Valid ValidCanPayOrderForm form) {
        Map param = BeanUtil.beanToMap(form);
        HashMap map = orderService.validCanPayOrder(param);
        return R.ok().put("result", map);
    }

    @PostMapping("/updateOrderPrepayId")
    @Operation(summary = "更新预支付订单ID")
    public R updateOrderPrepayId(@RequestBody @Valid UpdateOrderPrepayIdForm form) {
        Map param = BeanUtil.beanToMap(form);
        int rows = orderService.updateOrderPrepayId(param);
        return R.ok().put("rows", rows);
    }

    @RequestMapping("/receiveMessage")
    @Operation(summary = "接收代驾费消息通知")
    public void receiveMessage(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.setCharacterEncoding("UTF-8");
        Reader reader = request.getReader();
        BufferedReader buffer = new BufferedReader(reader);
        String line = buffer.readLine();
        StringBuffer temp = new StringBuffer();
        while (line != null) {
            temp.append(line);
            line = buffer.readLine();
        }
        buffer.close();
        reader.close();
        String xml = temp.toString();
        if (WXPayUtil.isSignatureValid(xml, myWXPayConfig.getKey())) {
            Map<String, String> map = WXPayUtil.xmlToMap(xml);
            String resultCode = map.get("result_code");
            String returnCode = map.get("return_code");
            if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/xml");
                Writer writer = response.getWriter();
                BufferedWriter bufferedWriter = new BufferedWriter(writer);
                bufferedWriter.write("<xml><return_code><![CDATA[SUCCESS]]></return_code> <return_msg><![CDATA[OK]]></return_msg></xml>");
                bufferedWriter.close();
                writer.close();
                String uuid = map.get("out_trade_no");
                String payId = map.get("transaction_id");
                String driverOpenId = map.get("attach");
                String payTime = DateUtil.parse(map.get("time_end"), "yyyyMMddHHmmss").toString("yyyy-MM-dd HH:mm:ss");
                // 修改订单状态、执行分账、发放系统奖励
                orderService.handlePayment(uuid, payId, driverOpenId, payTime);
            } else {
                response.sendError(500, "数字签名异常");
            }
        }
    }
}
