package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.DeleteCustomerCarByIdForm;
import com.example.hxds.bff.customer.controller.form.InsertCustomerCarForm;
import com.example.hxds.bff.customer.controller.form.SearchCustomerCarListForm;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-11-20 00:36
 **/
public interface CustomerCarService {

    void insertCustomerCar(InsertCustomerCarForm form);

    ArrayList<HashMap> searchCustomerCarList(SearchCustomerCarListForm form);

    int deleteCustomerCarById(DeleteCustomerCarByIdForm form);

}
