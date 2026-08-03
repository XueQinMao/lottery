package com.my.project;

import com.alibaba.fastjson.JSON;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.support.FileUtils;
import com.my.project.service.support.SsqCombinationUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Main
 *
 * @author 刘强
 * @version 2025/09/05 15:24
 **/
public class Main {

    public static void main(String[] args) {

//        FileUtils.readLine("E:\\home\\python\\/2026-07-28_预测结果.txt",
//            content -> Optional.of(content).map(s -> s.split("#"))
//                .map(array -> Pair.of(array[0].split("\\|"), JSON.parseObject(array[1], ModelPredictOutputBo.class)))
//                .map(pair -> {
//                    PredictRecord result = new PredictRecord();
//                    result.setOpenDate(LocalDate.now());
//                    result.setRedBalls(pair.getLeft()[1]);
//                    result.setBlueBall(Integer.valueOf(pair.getLeft()[2]));
//                    result.setExplanation(pair.getRight().getReason());
//                    result.setTotalScore(pair.getRight().getProbability());
//                    return result;
//                }).ifPresent(r -> System.out.println("main = "+JSON.toJSONString(r)))
//        );

        SsqCombinationUtils.generateNaturalRandom(1000, System.out::println);
    }

}
