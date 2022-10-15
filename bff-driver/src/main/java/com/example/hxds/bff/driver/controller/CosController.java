package com.example.hxds.bff.driver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.hxds.bff.driver.controller.form.DeleteCosFileForm;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.CosUtil;
import com.example.hxds.common.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;

/**
 * @program: hxds
 * @description:
 * @author: noah2021
 * @date: 2022-10-14 00:45
 **/
//TODO:common模块读取配置文件异常
//@RestController
@RequestMapping("/cos")
@Slf4j
@Tag(name = "CosController", description = "对象存储Web接口")
public class CosController {

    @Resource
    private CosUtil cosUtil;

    @PostMapping("/uploadCosPrivateFile")
    @SaCheckLogin
    @Operation(summary = "上传文件")
    public R uploadCosPrivateFile(@Param("file") MultipartFile file,@Param("module") String module){
        if(file.isEmpty()){
            throw new HxdsException("上传文件不能为空");
        }
        try{
            String path=null;
            if("driverAuth".equals(module)){
                path="/driver/auth/";
            }
            else{
                throw new HxdsException("module错误");
            }
            HashMap map=cosUtil.uploadPrivateFile(file,path);
            return R.ok(map);
        }catch (Exception e){
            log.error("文件上传到腾讯云错误", e);
            throw new HxdsException("文件上传到腾讯云错误");
        }

    }

    @PostMapping("/deleteCosPrivateFile")
    @SaCheckLogin
    @Operation(summary = "删除文件")
    public R deleteCosPrivateFile(@Valid @RequestBody DeleteCosFileForm form){
        cosUtil.deletePrivateFile(form.getPathes());
        return R.ok();
    }
}

