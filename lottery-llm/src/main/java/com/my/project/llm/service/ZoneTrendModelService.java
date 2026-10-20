package com.my.project.llm.service;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.FeatureTrendInference;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.bo.ZoneTrendInference;
import com.my.project.llm.bo.ZoneTrendSnapshot;
import com.my.project.llm.client.VisionModelClient;
import com.my.project.llm.prompt.FeatureTrendPrompt;
import com.my.project.llm.support.FeatureTrendInferenceParser;
import com.my.project.llm.support.ZoneTrendInferenceParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneTrendModelService {
    private final VisionModelClient client;

    public String modelIdentity() { return client.modelIdentity(); }

    public String modelName() { return client.modelName(); }

    public ZoneTrendInference analyze(ZoneTrendSnapshot snapshot, List<ZoneTrendImage> images) {
        return client.call("zoneTrend", snapshot.endPeriod(), images, request -> request.user(user -> user
            .text("以下是图片对应的后台逐期特征数据。目标为截止期之后的下一期开奖，不推测下一期的期号。\n{snapshot}")
            .param("snapshot", JSON.toJSONString(snapshot))), content -> ZoneTrendInferenceParser.parse(content, snapshot.periods()));
    }

    public FeatureTrendInference analyzeFeature(FeatureTrendKind kind, FeatureTrendInput input,
                                                List<ZoneTrendImage> images) {
        return client.call(kind.code(), input.endPeriod(), images, request -> request.system(FeatureTrendPrompt.SYSTEM).user(user -> user
            .text("""
                本次推演 {ball}{feature}。目标为截止期之后的下一期开奖。
                候选值格式：{valueHint}。
                下列JSON为送审图组对应的后台逐期特征，数组的同一索引对应同一期号：
                {snapshot}
                """)
            .param("ball", kind.blue() ? "蓝球" : "红球").param("feature", kind.label())
            .param("valueHint", kind.valueHint()).param("snapshot", JSON.toJSONString(input))),
            content -> FeatureTrendInferenceParser.parse(content, kind, input.periods()));
    }

}
