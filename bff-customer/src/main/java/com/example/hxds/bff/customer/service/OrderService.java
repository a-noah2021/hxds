package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.*;

import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2023-01-18 00:12
 **/
public interface OrderService {

    HashMap createNewOrder(CreateNewOrderForm form);

    Integer searchOrderStatus(SearchOrderStatusForm form);

    String deleteUnAcceptOrder(DeleteUnAcceptOrderForm form);

    HashMap hasCustomerCurrentOrder(HasCustomerCurrentOrderForm form);

    HashMap searchOrderForMoveById(SearchOrderForMoveByIdForm form);

    boolean confirmArriveStartPlace(ConfirmArriveStartPlaceForm form);

    HashMap searchOrderById(SearchOrderByIdForm form);

    HashMap createWxPayment(long orderId, long customerId, Long customerVoucherId, Long voucherId);

    String updateOrderAboutPayment(UpdateOrderAboutPaymentForm form);

}
