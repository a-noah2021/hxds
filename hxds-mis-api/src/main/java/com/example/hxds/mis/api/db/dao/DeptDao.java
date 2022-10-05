package com.example.hxds.mis.api.db.dao;


import com.example.hxds.mis.api.db.pojo.DeptEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface DeptDao {
    public ArrayList<HashMap> searchAllDept();

    public HashMap searchById(int id);

    public ArrayList<HashMap> searchDeptByPage(Map param);

    public long searchDeptCount(Map param);

    public boolean searchCanDelete(Integer[] ids);

    public int insert(DeptEntity dept);

    public int update(DeptEntity dept);

    public int deleteDeptByIds(Integer[] ids);
}




