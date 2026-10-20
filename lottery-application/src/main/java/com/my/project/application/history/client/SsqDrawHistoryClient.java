package com.my.project.application.history.client;

import cn.hutool.http.HttpException;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SsqDrawHistoryClient {
    private static final Logger logger = LoggerFactory.getLogger(SsqDrawHistoryClient.class);
    private static final String URL_FORMAT =
        "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=%s&pageNo=%d&pageSize=10&systemType=PC";

    public List<WebsiteDrawItemDto> fetchNewerThan(int pageNumber, LocalDate lastOpenDate) {
        return fetchPage(pageNumber, 0).stream()
            .filter(row -> lastOpenDate == null || parseDate(row.getDate()).isAfter(lastOpenDate)).toList();
    }

    private List<WebsiteDrawItemDto> fetchPage(int pageNum, int retry) {
        try {
            var response = JSON.parseObject(HttpUtil.get(String.format(URL_FORMAT, "ssq", pageNum), 15000),
                SsqWebsiteClientDto.class);
            if (response == null || response.getState() != 0 || response.getResult() == null) {
                throw new IllegalStateException("福彩官网未返回有效的开奖记录");
            }
            logger.info("福彩开奖查询成功，页码={}，记录数={}", pageNum, response.getResult().size());
            return response.getResult();
        } catch (HttpException | JSONException | IllegalStateException failure) {
            if (retry < 2) return fetchPage(pageNum, retry + 1);
            throw new IllegalStateException("福彩开奖查询失败，请稍后重试", failure);
        }
    }

    public static LocalDate parseDate(String dateString) {
        var pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
        var matcher = pattern.matcher(dateString);

        if (matcher.find()) {
            var dateStr = matcher.group(1); // 提取 "2022-11-17"
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(dateStr, formatter); // 转换为 LocalDate
        } else {
            throw new IllegalArgumentException("无法从输入中提取日期: " + dateString);
        }
    }

}
