package com.example.hxds.bff.customer.service;

import com.example.hxds.bff.customer.controller.form.LoginForm;
import com.example.hxds.bff.customer.controller.form.RegisterNewCustomerForm;

public interface CustomerService {

    long registerNewCustomer(RegisterNewCustomerForm form);

    Long login(LoginForm form);
}

