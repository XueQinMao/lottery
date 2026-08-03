package com.my.project.service.history.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;
import com.my.project.service.history.pojo.client.SsqWebsiteClientDto;
import com.my.project.service.history.pojo.client.WebsiteDrawItemDto;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
@Service
@Primary
public class HistoryRecordServiceImpl implements IHistoryRecordService {

    private final Logger logger = LoggerFactory.getLogger(HistoryRecordServiceImpl.class);

    private static final String URL_FORMAT =
        "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=%s&pageNo=%d&pageSize=10&systemType=PC";

    private final IHistoryRecordRepository historyRecordRepository;

    public HistoryRecordServiceImpl(IHistoryRecordRepository historyRecordRepository) {
        this.historyRecordRepository = historyRecordRepository;
    }

    @Override
    public void syncHistoryRecords() {
        var lastRecord =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit 1").one();
        List<WebsiteDrawItemDto> results = null;
        var pageNum = new AtomicInteger(1);
        do {
            results = fetchByPageNumber(pageNum.getAndAdd(1), NumberUtils.INTEGER_ZERO,
                Objects.isNull(lastRecord) ? null : lastRecord.getOpenDate());
            var list = Optional.ofNullable(results).orElse(Collections.emptyList()).stream().map(mapper()).toList();
            if (CollectionUtil.isNotEmpty(list)) {
                historyRecordRepository.saveOrUpdateBatch(list);
            }
        } while (CollectionUtil.isNotEmpty(results));
    }

    private List<WebsiteDrawItemDto> fetchByPageNumber(Integer pageNum, Integer retry, LocalDate lastOpenDate) {
        Assert.isTrue(retry < 3, "重试次数超过3次，请检查网络");
        SsqWebsiteClientDto resp = null;
        try {
            var respStr = HttpUtil.get(String.format(URL_FORMAT, "ssq", pageNum), 15000);
            Assert.notBlank(respStr, "http response is null");
            resp = JSON.parseObject(respStr, SsqWebsiteClientDto.class);
            logger.info("请求成功，开始处理数据 {}", respStr);
        } catch (Exception e) {
            if (e instanceof HttpException || e instanceof JSONException) {
                return fetchByPageNumber(pageNum, retry + 1, lastOpenDate);
            }
            logger.error("请求失败，请检查网络", e);
        }
        return Optional.ofNullable(resp).map(SsqWebsiteClientDto::getResult).orElse(Collections.emptyList()).stream()
            .map(r -> Pair.of(parseDate(r.getDate()), r))
            .filter(r -> Objects.isNull(lastOpenDate) || r.getLeft().isAfter(lastOpenDate)).map(Pair::getRight)
            .collect(Collectors.toList());
    }

    @Override
    public Page<HistoryRecordDto> findPage(Page<HistoryRecordDto> page) {
        var resultPage = historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate)
            .page(new Page<>(page.getCurrent(), page.getSize()));
        var collect = CollectionUtil.emptyIfNull(resultPage.getRecords()).stream()
            .map(r -> BeanUtil.copyProperties(r, HistoryRecordDto.class)).collect(Collectors.toList());
        return page.setRecords(collect);
    }

    private Function<WebsiteDrawItemDto, HistoryRecord> mapper() {
        return item -> {
            String[] split = item.getRed().split(",");
            return HistoryRecord.builder().period(item.getCode()).type("SSQ").openDate(parseDate(item.getDate()))
                .num1(Integer.valueOf(split[0])).num2(Integer.valueOf(split[1])).num3(Integer.valueOf(split[2]))
                .num4(Integer.valueOf(split[3])).num5(Integer.valueOf(split[4])).num6(Integer.valueOf(split[5]))
                .special(Integer.valueOf(item.getBlue())).build();
        };
    }

    private LocalDate parseDate(String dateString) {
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

    @Override
    public List<HistoryRecord> getLatestRecords(int count) {
        return historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit " + count)
            .list();
    }
}
