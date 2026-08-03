package com.my.project.service.history.pojo.client;

import lombok.Data;

import java.util.List;

/**
 * 双色球官网开奖公告响应（外部协议反序列化）
 *
 * @author 刘强
 * @version 2025/07/17 16:42
 **/
@Data
public class SsqWebsiteClientDto {

  private int state;
  private String message;
  private int total;
  private int pageNum;
  private int pageNo;
  private int pageSize;
  private List<WebsiteDrawItemDto> result;
}
