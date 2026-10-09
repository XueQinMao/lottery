package com.my.project.api.request;

import lombok.Data;

import java.util.List;

/**
 * PredictionCacheRequest
 *
 * @author 刘强
 * @version 2026/07/28 20:07
 **/
@Data
public class PredictionCacheRequest {

    private Integer size;

    private List<String> keys;
}
