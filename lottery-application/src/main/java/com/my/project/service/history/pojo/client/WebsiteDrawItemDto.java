package com.my.project.service.history.pojo.client;

import lombok.Data;

/**
 * 官网开奖公告单条记录（外部协议反序列化）
 *
 * @author 刘强
 * @version 2025/07/17 16:42
 **/
@Data
public class WebsiteDrawItemDto {
    private String name;
    private String code;
    private String date;
    private String week;
    private String red;
    private String blue;
    private String blue2;
}
