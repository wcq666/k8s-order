package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private String code;
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result ok(){
        return new Result(null,true, null, null, null);
    }
    public static Result ok(String code){
        return new Result(code,true, null, null, null);
    }
    public static Result ok(Object data){
        return new Result(null,true, null, data, null);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(null,true, null, data, total);
    }
    public static Result fail(String errorMsg){
        return new Result(null,false, errorMsg, null, null);
    }

}
