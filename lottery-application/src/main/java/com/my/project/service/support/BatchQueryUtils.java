package com.my.project.service.support;

import cn.hutool.core.collection.CollectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按 ID 分段批处理。查询与加载都由调用方以函数传入，不绑定具体 Repository。
 **/
public final class BatchQueryUtils {

    private static final Logger logging = LoggerFactory.getLogger(BatchQueryUtils.class);

    public static final int DEFAULT_ENTITY_BATCH = 10_000;
    public static final int DEFAULT_ID_BATCH = 100;

    private BatchQueryUtils() {
    }

    /**
     * 先拉全量 ID，再按批加载实体后交给 consumer。
     *
     * @param idLoader    查询全部待处理 ID（建议按 ID 升序）
     * @param batchLoader 按一批 ID 加载实体，例如 {@code ids -> repo.lambdaQuery().in(Entity::getId, ids).list()}
     * @param consumer    处理本批实体
     */
    public static <ID, T> void process(Supplier<List<ID>> idLoader, Function<List<ID>, List<T>> batchLoader,
            Consumer<List<T>> consumer) {
        process(idLoader, batchLoader, consumer, DEFAULT_ENTITY_BATCH);
    }

    public static <ID, T> void process(Supplier<List<ID>> idLoader, Function<List<ID>, List<T>> batchLoader,
            Consumer<List<T>> consumer, int batchSize) {
        List<ID> allIds = idLoader.get();
        logging.info("查询总条数 {}", allIds.size());
        if (CollectionUtil.isEmpty(allIds)) {
            return;
        }
        CollectionUtil.split(allIds, batchSize).parallelStream().forEach(ids -> {
            List<T> list = batchLoader.apply(ids);
            if (CollectionUtil.isNotEmpty(list)) {
                consumer.accept(list);
            }
        });
    }

    /**
     * 只按 ID 分段处理（删除、按 ID 再查等）。
     */
    public static <ID> void processIds(Supplier<List<ID>> idLoader, Consumer<List<ID>> consumer) {
        processIds(idLoader, consumer, DEFAULT_ID_BATCH);
    }

    public static <ID> void processIds(Supplier<List<ID>> idLoader, Consumer<List<ID>> consumer, int batchSize) {
        List<ID> allIds = idLoader.get();
        logging.info("查询总条数 {}", allIds.size());
        if (CollectionUtil.isEmpty(allIds)) {
            return;
        }
        CollectionUtil.split(allIds, batchSize).parallelStream().forEach(consumer);
    }
}
