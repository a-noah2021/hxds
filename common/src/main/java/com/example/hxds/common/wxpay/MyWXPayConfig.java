package com.example.hxds.common.wxpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;

@Component
public class MyWXPayConfig extends WXPayConfig {
    @Value("${wx.app-id}")
    private String appId;

    @Value("${wx.mch-id}")
    private String mchId;

    @Value("${wx.key}")
    private String key;

    @Value("${wx.cert-path}")
    private String certPath;

    private byte[] certData;

    @PostConstruct
    public void init() throws Exception {
        File file = new File(certPath);
        FileInputStream in = new FileInputStream(file);
        BufferedInputStream bin = new BufferedInputStream(in);
        this.certData = new byte[(int) file.length()];
        bin.read(this.certData);
        bin.close();
        in.close();
    }

    @Override
    public String getAppID() {
        return appId;
    }

    @Override
    public String getMchID() {
        return mchId;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    InputStream getCertStream() {
        ByteArrayInputStream in = new ByteArrayInputStream(this.certData);
        return in;
    }

    @Override
    IWXPayDomain getWXPayDomain() {
        return new IWXPayDomain() {
            @Override
            public void report(String domain, long elapsedTimeMillis, Exception ex) {
            }

            @Override
            public DomainInfo getDomain(WXPayConfig config) {
                return new DomainInfo(WXPayConstants.DOMAIN_API, true);
            }
        };
    }
}
