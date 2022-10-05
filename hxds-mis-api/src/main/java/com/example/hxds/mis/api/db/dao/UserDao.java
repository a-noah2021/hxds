package com.example.hxds.mis.api.db.dao;

import com.example.hxds.mis.api.db.pojo.UserEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface UserDao {
    public Set<String> searchUserPermissions(int userId);

    public HashMap searchById(int userId);

    public HashMap searchUserSummary(int userId);

    public HashMap searchUserInfo(int userId);

    public Integer searchDeptManagerId(int id);

    public Integer searchGmId();

    public ArrayList<HashMap> searchAllUser();

    public Integer login(Map param);

    public int updatePassword(Map param);

    public ArrayList<HashMap> searchUserByPage(Map param);

    public long searchUserCount(Map param);

    public int insert(UserEntity user);

    public int update(Map param);

    public int deleteUserByIds(Integer[] ids);

    public ArrayList<String> searchUserRoles(int userId);

    public HashMap searchNameAndDept(int userId);
}




