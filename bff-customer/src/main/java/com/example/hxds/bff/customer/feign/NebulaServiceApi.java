package com.example.hxds.bff.customer.feign;

import com.example.hxds.bff.customer.config.MultipartSupportConfig;
import com.example.hxds.common.util.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(value = "hxds-nebula", configuration = MultipartSupportConfig.class)
public interface NebulaServiceApi {

    @PostMapping(value = "/monitoring/uploadRecordFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R uploadRecordFile(@RequestPart(value = "file") MultipartFile file,
                              @RequestPart("name") String name,
                              @RequestPart(value = "text", required = false) String text);

}
