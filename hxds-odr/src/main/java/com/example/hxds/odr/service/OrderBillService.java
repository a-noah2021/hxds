package com.example.hxds.odr.service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhanglumin
 * @since 2023/8/24 7:42 PM
 */
public interface OrderBillService {

    int updateBillFee(Map param);

    HashMap searchReviewDriverOrderBill(Map param);
}
