package com.my.project.api.scheduled;

import com.my.project.service.startup.handler.StartupChainAssembler;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LotteryScheduled
 *
 * @author 刘强
 * @version 2026/02/05 19:38
 **/
@Component
public class LotteryScheduled {

    @Resource
    private StartupChainAssembler chainCombination;

    @Scheduled(cron = "0 0 22 ? * TUE,THU,SUN")
    public void runTask() {
//        LocalDate localDate = NextLotteryDateUtils.prevDrawDate(1);
//        List<AbstractStartupChain> chains = chainCombination.assembleChain(localDate);
//        for (int i = 0; i < chains.size() - 1; i++) {
//            chains.get(i).setNext(chains.get(i + 1));
//        }
//        StartupContextBo<Object> objectStartupContextBo = new StartupContextBo<>();
//        objectStartupContextBo.setOpenDate(localDate);
//        chains.getFirst().handle(objectStartupContextBo);

    }
}
