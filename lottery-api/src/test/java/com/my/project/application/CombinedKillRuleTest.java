package com.my.project.application;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.infrastructure.persistence.repository.impl.HistoryRecordRepositoryImpl;
import com.my.project.infrastructure.persistence.mapper.HistoryRecordMapper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import jakarta.annotation.Resource;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.PrintStream;
import java.util.List;

/** 统一数据库回测入口：一次读取历史，分别统计两来源，再统计合并去重结果。 */
@SpringBootTest(classes = CombinedKillRuleTest.DatabaseConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("今日头条与小红书双色球杀号统一回测")
public class CombinedKillRuleTest {
    // 只装配数据库访问，避免回测启动定时任务或无关的LLM/HTTP客户端。
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class})
    @MapperScan(basePackageClasses = HistoryRecordMapper.class)
    @Import(HistoryRecordRepositoryImpl.class)
    static class DatabaseConfig { }

    @Resource
    private HistoryRecordRepository historyRecordRepository;

    @Test
    @DisplayName("筛选规则：两来源明细、各规则错杀率及合并并集统计")
    public void statCombinedWrongKillRate() {
        int periods = Integer.getInteger("kill.backtest.periods", 100);
        // 与 CustomizeTest 一样使用数据库历史记录；一次读取，避免逐规则查询以及未来数据泄漏。
        var history = historyRecordRepository.lambdaQuery().orderByAsc(HistoryRecord::getPeriod).list();
        runBacktests(history, periods, System.out);
    }

    static void runBacktests(List<HistoryRecord> history, int periods, PrintStream out) {
        out.printf("统一回测：最近%d期；头条与小红书使用同一份历史、同一目标期范围。%n", periods);
        var toutiao = ToutiaoKillRules.catalog();
        var xiaohongshu = XiaohongshuKillRules.catalog();
        out.println("只统计追加的头条/小红书规则；现有KillBallUtils杀球方式原样保留。");

        out.println("\n========== 第一部分：今日头条 ==========");
        ToutiaoKillRules.backtest(history, periods, out, toutiao, "头条");
        out.println("\n========== 第二部分：小红书 ==========");
        XiaohongshuKillRules.backtest(history, periods, out, xiaohongshu);

        var combined = ToutiaoKillRules.integratedCatalog();
        out.println("\n========== 第三部分：头条+小红书追加规则合并（同号码仅计一次） ==========");
        out.println("合并可能增加错杀，最终结果以本部分的逐期并集和总体汇总为准。");
        ToutiaoKillRules.backtest(history, periods, out, combined, "头条+小红书");
    }
}
