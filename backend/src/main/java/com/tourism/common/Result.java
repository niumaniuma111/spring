package com.tourism.common;

/** 统一响应结构 */
public class Result<T> {
    private int code;      // 0 成功，非 0 失败
    private String msg;
    private T data;

    public Result() {}
    public Result(int code, String msg, T data) { this.code = code; this.msg = msg; this.data = data; }

    public static <T> Result<T> ok(T data) { return new Result<>(0, "success", data); }
    public static <T> Result<T> ok() { return ok(null); }
    public static <T> Result<T> fail(int code, String msg) { return new Result<>(code, msg, null); }
    public static <T> Result<T> fail(String msg) { return fail(1, msg); }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
