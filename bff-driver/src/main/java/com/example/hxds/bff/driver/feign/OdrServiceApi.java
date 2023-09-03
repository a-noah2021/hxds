package com.example.hxds.bff.driver.feign;

import com.example.hxds.bff.driver.controller.form.*;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "hxds-odr")
public interface OdrServiceApi {

    @PostMapping("/order/searchDriverTodayBusinessData")
    R searchDriverTodayBusinessData(SearchDriverTodayBusinessDataForm form);

    @PostMapping("/order/acceptNewOrder")
    R acceptNewOrder(AcceptNewOrderForm form);

    @PostMapping("/order/searchDriverExecuteOrder")
    R searchDriverExecuteOrder(SearchDriverExecuteOrderForm form);

    @PostMapping("/order/searchDriverCurrentOrder")
    R searchDriverCurrentOrder(SearchDriverCurrentOrderForm form);

    @PostMapping("/order/searchOrderForMoveById")
    R searchOrderForMoveById(SearchOrderForMoveByIdForm form);

    @PostMapping("/order/arriveStartPlace")
    R arriveStartPlace(ArriveStartPlaceForm form);

    @PostMapping("/order/startDriving")
    R startDriving(StartDrivingForm form);

    @PostMapping("/order/updateOrderStatus")
    R updateOrderStatus(UpdateOrderStatusForm form);

    @PostMapping("/order/validDriverOwnOrder")
    R validDriverOwnOrder(ValidDriverOwnOrderForm form);

    @PostMapping("/order/searchSettlementNeedData")
    R searchSettlementNeedData(SearchSettlementNeedDataForm form);

    @PostMapping("/bill/updateBillFee")
    R updateBillFee(UpdateBillFeeForm form);

    @PostMapping("/bill/searchReviewDriverOrderBill")
    R searchReviewDriverOrderBill(SearchReviewDriverOrderBillForm form);

    @PostMapping("/order/searchOrderStatus")
    R searchOrderStatus(SearchOrderStatusForm form);

    @PostMapping("/order/updateOrderAboutPayment")
    R updateOrderAboutPayment(UpdateOrderAboutPaymentForm form);
}
