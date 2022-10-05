package com.example.hxds.mis.api.db.dao;


import com.example.hxds.mis.api.db.pojo.RoleEntity;

import java.util.ArrayList;
import java.util.HashMap;

public interface RoleDao {
    public ArrayList<HashMap> searchAllRole();

    public HashMap searchById(int id);

    public ArrayList<HashMap> searchRoleByPage(HashMap param);

    public long searchRoleCount(HashMap param);

    public int insert(RoleEntity role);

    public ArrayList<Integer> searchUserIdByRoleId(int roleId);

    public int update(RoleEntity role);

    public boolean searchCanDelete(Integer[] ids);

    public int deleteRoleByIds(Integer[] ids);
}




