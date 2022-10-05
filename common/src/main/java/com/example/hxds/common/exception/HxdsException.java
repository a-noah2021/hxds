package com.example.hxds.common.exception;

import lombok.Data;

@Data
public class HxdsException extends RuntimeException {
    private String msg;
    private int code = 500;

    public HxdsException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public HxdsException(String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
    }

    public HxdsException(String msg, int code) {
        super(msg);
        this.msg = msg;
        this.code = code;
    }

    public HxdsException(String msg, int code, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.code = code;
    }

}