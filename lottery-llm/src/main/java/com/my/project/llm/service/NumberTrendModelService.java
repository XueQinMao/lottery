package com.my.project.llm.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.my.project.llm.bo.NumberTrendObservation;
import com.my.project.llm.bo.NumberTrendObservation.Ball;
import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.client.VisionModelClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NumberTrendModelService {
    private static final int MAX_RED_CANDIDATES = 2;
    private static final int MAX_BLUE_CANDIDATES = 1;
    private static final int MAX_OUTPUT_TOKENS = 128;
    private static final int RED_STREAK_TO_SKIP = 3;
    private static final int RED_ZONE_SIZE = 11;
    private static final int BLUE_ZONE_SIZE = 4;
    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .build();
    private static final String SYSTEM = """
        对照双色球录入数据与两张走势图。历史行从上到下由旧到新，实心红蓝圆点表示开出。
        红球三区为 01–11、12–22、23–33。利用区间辅助线和 zoneHistory.redZoneCounts，先看三区各期出球数的增减、
        聚集和转移，再沿红球形态线观察各区内的延续、折返、连号、跨期重号和跨区的走向。
        综合判断线条指向的号码与其所在区的近期走势是否一致。红色虚线和 Next (est.) 行用于核对外推方向，
        redLineCandidates 提供对应候选及真实开出点。跳过 redStreaksToSkip 中的红球，遗漏供辅助判断。
        蓝球四区为 01–04、05–08、09–12、13–16。沿 zoneHistory.blueZone 和蓝球走势线观察各区停留、往返与跨区跳转，
        把最近四期的区间及号码变化与历史片段对比。blueHistory.matches 提供号码方向相同的片段、跳幅差，
        以及其后实际的 nextBlue 和 nextStep。结合区域走向、跳幅及历史后续方向筛选蓝球。
        只输出候选号，由优先到次优排列。red 最多2个互异整数（1–33），blue 最多1个整数（1–16）。
        某种球缺少可靠候选时填空数组。只返回一个 JSON 对象，字段固定为 red 和 blue。
        {"red":[7,14],"blue":[11]}
        """;

    private final VisionModelClient client;

    public List<NumberTrendObservation> analyze(List<Draw> draws, List<ZoneTrendImage> images) {
        return analyze(draws, images, List.of());
    }

    public List<NumberTrendObservation> analyze(List<Draw> draws, List<ZoneTrendImage> images,
                                                List<NumberTrendObservation> redLineCandidates) {
        var redStreaks = redStreaksToSkip(draws);
        var eligibleRedCandidates = redLineCandidates.stream()
            .filter(candidate -> !redStreaks.contains(candidate.number())).toList();
        var zoneHistory = draws.stream().map(NumberTrendModelService::zoneDraw).toList();
        var review = new ReviewInput(draws, zoneHistory, redStreaks, eligibleRedCandidates, BlueTrendComparison.compare(draws));
        return client.call("numberTrend", draws.getLast().period(), images, MAX_OUTPUT_TOKENS,
            request -> request.system(SYSTEM)
                .user(user -> user.text("以下是逐期开奖、分区走势和蓝球历史对照。\n{review}")
                    .param("review", JSON.toJSONString(review))),
            content -> parse(content, review));
    }

    private static ZoneDraw zoneDraw(Draw draw) {
        int[] redZoneCounts = new int[Ball.RED.count() / RED_ZONE_SIZE];
        for (int number : draw.reds()) redZoneCounts[(number - 1) / RED_ZONE_SIZE]++;
        int blueZone = (draw.blue() - 1) / BLUE_ZONE_SIZE + 1;
        return new ZoneDraw(draw.period(), redZoneCounts, blueZone);
    }

    private static Set<Integer> redStreaksToSkip(List<Draw> draws) {
        if (draws.size() < RED_STREAK_TO_SKIP) return Set.of();
        var streaks = new TreeSet<>(draws.getLast().reds());
        for (int offset = 2; offset <= RED_STREAK_TO_SKIP; offset++) {
            streaks.retainAll(draws.get(draws.size() - offset).reds());
        }
        return streaks;
    }

    private static List<NumberTrendObservation> parse(String content, ReviewInput review) {
        var json = content.strip();
        if (json.startsWith("```json") && json.endsWith("```")) {
            json = json.substring("```json".length(), json.length() - "```".length()).strip();
        }
        Response response;
        try {
            response = MAPPER.readValue(json, Response.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("号码走势图分析返回的 JSON 不完整或字段不正确", e);
        }
        Assert.notNull(response, "缺少走势图候选");
        validateCandidates(response.red(), Ball.RED, MAX_RED_CANDIDATES);
        validateCandidates(response.blue(), Ball.BLUE, MAX_BLUE_CANDIDATES);

        var selectedReds = new LinkedHashSet<>(response.red());
        selectedReds.removeAll(review.redStreaksToSkip());
        // 保留模型选中的红球顺序，随后补入图表已算出的候选。
        var reds = selectedReds.stream().map(number -> new NumberTrendObservation(Ball.RED, number));
        var blues = response.blue().stream().map(number -> new NumberTrendObservation(Ball.BLUE, number));
        var otherReds = review.redLineCandidates().stream()
            .filter(item -> !selectedReds.contains(item.number()))
            .map(item -> new NumberTrendObservation(Ball.RED, item.number()));
        return Stream.concat(Stream.concat(reds, blues), otherReds).toList();
    }

    private static void validateCandidates(List<Integer> numbers, Ball ball, int maxCandidates) {
        Assert.isTrue(numbers != null && numbers.size() <= maxCandidates, "走势图候选数量有误");
        Assert.isTrue(numbers.stream().allMatch(number -> number != null && number >= 1 && number <= ball.count()),
            "走势图候选号超出范围");
        Assert.isTrue(new HashSet<>(numbers).size() == numbers.size(), "走势图候选号重复");
    }

    public record Draw(String period, List<Integer> reds, int blue, List<Integer> redOmissions,
                       List<Integer> blueOmissions) {
        public List<Integer> omissions(Ball ball) {
            return ball == Ball.RED ? redOmissions : blueOmissions;
        }
    }

    private record Response(List<Integer> red, List<Integer> blue) { }
    private record ZoneDraw(String period, int[] redZoneCounts, int blueZone) { }
    private record ReviewInput(List<Draw> draws, List<ZoneDraw> zoneHistory, Set<Integer> redStreaksToSkip,
                               List<NumberTrendObservation> redLineCandidates, BlueTrendComparison blueHistory) { }
}
