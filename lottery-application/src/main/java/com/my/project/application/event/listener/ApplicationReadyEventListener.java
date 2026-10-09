package com.my.project.application.event.listener;

import com.alibaba.fastjson.JSON;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.startup.handler.AbstractStartupChain;
import com.my.project.application.startup.handler.StartupChainAssembler;
import com.my.project.application.selection.model.StartupContextBo;
import com.my.project.application.support.FileUtils;
import com.my.project.application.support.NextLotteryDateUtils;
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

    private final StartupChainAssembler startupChainAssembler;

    private final LotteryModelProperties modelProperties;

    private final PredictCacheService predictCacheService;

    @EventListener
    public void handleEvent(ApplicationReadyEvent event) {
        CompletableFuture.runAsync(() -> loadingDiskToCache(NextLotteryDateUtils.nextDrawDate()))
            .thenRun(() -> log.info("loading disk to cache is ok size"));
        List<AbstractStartupChain> list = startupChainAssembler.assembleChain(NextLotteryDateUtils.prevDrawDate());
        for (int i = 0; i < list.size() - 1; i++) {
            list.get(i).setNext(list.get(i + 1));
        }
//        list.getFirst().handle(new StartupContextBo<>());
    }

    /**
     * 加载磁盘数据到缓存
     */
    private void loadingDiskToCache(LocalDate localDate) {
        FileUtils.readLine(modelProperties.getPath() + "/" + localDate + "_cache_persistence.txt", content -> {
            var split = content.split("#");
            predictCacheService.addCache(split[0], JSON.parseObject(split[1], ModelPredictOutputBo.class));
        });
    }
}
