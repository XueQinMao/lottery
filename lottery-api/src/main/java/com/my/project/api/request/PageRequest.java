package com.my.project.api.request;

import lombok.Data;

/**
 * 分页请求参数
 *
 * @author 刘强
 * @version 2025/08/01 17:01
 **/
@Data
public class PageRequest {

  private Integer pageNum;
  private Integer pageSize;
}
