package com.my.project.service.event.listener;

import com.alibaba.fastjson.JSON;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.startup.handler.AbstractStartupChain;
import com.my.project.service.startup.handler.StartupChainAssembler;
import com.my.project.service.selection.pojo.bo.StartupContextBo;
import com.my.project.service.support.FileUtils;
import com.my.project.service.support.NextLotteryDateUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ApplicationReadyEventListener
 *
 * @author 刘强
 * @version 2025/08/05 16:40
 **/
@Component
@AllArgsConstructor
@Slf4j
public  class ApplicationReadyEventListener {

    private final StartupChainAssembler chainCombination;

    private final LotteryModelConfig lotteryModelConfig;

    private final IPredictCacheService predictCacheService;

    @EventListener
    public void handleEvent(ApplicationReadyEvent event) {
        CompletableFuture.runAsync(() -> loadingDiskToCache(NextLotteryDateUtils.nextDrawDate()))
            .thenRun(() -> log.info("loading disk to cache is ok size"));
        List<AbstractStartupChain> list = chainCombination.assembleChain(NextLotteryDateUtils.prevDrawDate());
        for (int i = 0; i < list.size() - 1; i++) {
            list.get(i).setNext(list.get(i + 1));
        }
//        list.getFirst().handle(new StartupContextBo<>());
    }

    /**
     * 加载磁盘数据到缓存
     */
    private void loadingDiskToCache(LocalDate localDate) {
        FileUtils.readLine(lotteryModelConfig.getPath() + "/" + localDate + "_cache_persistence.txt", content -> {
            var split = content.split("#");
            predictCacheService.addCache(split[0], JSON.parseObject(split[1], ModelPredictOutputBo.class));
        });
    }
}
