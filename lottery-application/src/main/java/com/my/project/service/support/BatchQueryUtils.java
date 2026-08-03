package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.my.project.persistence.repository.IPredictRecordRepository;
import com.my.project.persistence.entity.PredictRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * BatchQueryUtils
 *
 * @author 刘强
 * @version 2025/11/20 20:16
 **/
public class BatchQueryUtils {

    private static final Logger logging = LoggerFactory.getLogger(BatchQueryUtils.class);
    private final IPredictRecordRepository predictRecordRepository;

    public BatchQueryUtils(IPredictRecordRepository predictRecordRepository) {
        this.predictRecordRepository = predictRecordRepository;
    }

    /**
     * 第一步：查询总条数
     * 2、分段的去查询
     * @param openDate
     */
    public void process(LocalDate openDate, Consumer<List<PredictRecord>> consumer) {
        List<Long> allIds =
            predictRecordRepository.lambdaQuery().eq(PredictRecord::getOpenDate, openDate).select(PredictRecord::getId).orderByAsc(PredictRecord::getId)
                .list().stream().map(PredictRecord::getId).toList();
        logging.info("查询总条数 {}", allIds.size());

        CollectionUtil.split(allIds, 10000).parallelStream().forEach(ids ->{
            List<PredictRecord> list = predictRecordRepository.lambdaQuery().ge(PredictRecord::getId, ids.getFirst()).lt(PredictRecord::getId, ids.getLast()).list();
            consumer.accept(list);
        });
    }


    public void process(Consumer<LambdaQueryChainWrapper<PredictRecord>> queryConditionBuilder, Consumer<List<PredictRecord>> consumer) {
        LambdaQueryChainWrapper<PredictRecord> baseQuery = predictRecordRepository.lambdaQuery();
        if(null != queryConditionBuilder){
            queryConditionBuilder.accept(baseQuery);
        }
        List<Long> allIds = baseQuery// 使用合并后的查询条件
            .select(PredictRecord::getId).orderByAsc(PredictRecord::getId).list().stream().map(PredictRecord::getId)
            .toList();
        logging.info("查询总条数 {}", allIds.size());

        CollectionUtil.split(allIds, 10000).parallelStream().forEach(ids ->{
            List<PredictRecord> list = predictRecordRepository.lambdaQuery().ge(PredictRecord::getId, ids.getFirst()).lt(PredictRecord::getId, ids.getLast()).list();
            consumer.accept(list);
        });
    }

    public void processIds(LocalDate openDate, Consumer<List<Long>> consumer) {
        List<Long> allIds =
            predictRecordRepository.lambdaQuery().eq(PredictRecord::getOpenDate, openDate).select(PredictRecord::getId)
                .list().stream().map(PredictRecord::getId).toList();
        logging.info("查询总条数 {}", allIds.size());

        CollectionUtil.split(allIds, 100).parallelStream().forEach(consumer);
    }
}
