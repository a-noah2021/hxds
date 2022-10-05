package com.example.hxds.vhr.db.dao;


import com.example.hxds.vhr.db.pojo.VoucherEntity;

import java.util.ArrayList;

public interface VoucherDao {
    public int insert(VoucherEntity entity);

    public ArrayList<String> searchIdByUUID(ArrayList<String> list);
}




