package com.my.project.api.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * PredictCacheReq
 *
 * @author 刘强
 * @version 2026/07/28 20:07
 **/
@Data
public class PredictCacheReq {

    private Integer size;

    private List<String> keys;
}
