package com.example.hxds.mis.api.db.dao;

import com.example.hxds.mis.api.db.pojo.UserEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface UserDao {
    Set<String> searchUserPermissions(int userId);

    HashMap searchById(int userId);

    HashMap searchUserSummary(int userId);

    HashMap searchUserInfo(int userId);

    Integer searchDeptManagerId(int id);

    Integer searchGmId();

    ArrayList<HashMap> searchAllUser();

    Integer login(Map param);

    int updatePassword(Map param);

    ArrayList<HashMap> searchUserByPage(Map param);

    long searchUserCount(Map param);

    int insert(UserEntity user);

    int update(Map param);

    int deleteUserByIds(Integer[] ids);

    ArrayList<String> searchUserRoles(int userId);

    HashMap searchNameAndDept(int userId);
}




