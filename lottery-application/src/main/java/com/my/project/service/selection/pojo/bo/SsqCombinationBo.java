package com.my.project.service.selection.pojo.bo;

import lombok.Data;

import java.util.List;

/**
 * SsqCombinationBo
 *
 * @author 刘强
 * @version 2025/10/23 16:48
 **/
@Data
public class SsqCombinationBo {

    private List<Integer> redBalls;
    private int blueBall;

    public static SsqCombinationBo of(List<Integer> redBalls, int blueBall) {
        SsqCombinationBo bo = new SsqCombinationBo();
        bo.setBlueBall(blueBall);
        bo.setRedBalls(redBalls);
        return bo;
    }

    public void setRedBalls(List<Integer> redBalls) {
        this.redBalls = redBalls;
    }

    public void setBlueBall(int blueBall) {
        this.blueBall = blueBall;
    }
}
