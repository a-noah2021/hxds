package com.example.hxds.dr.db.dao;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface DriverDao {

    int registerNewDriver(Map param);

    long hasDriver(Map param);

    String searchDriverId(String openId);

}




