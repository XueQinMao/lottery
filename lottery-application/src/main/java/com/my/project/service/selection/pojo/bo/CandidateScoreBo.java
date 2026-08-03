package com.my.project.service.selection.pojo.bo;

/**
 * CandidateScoreBo
 *
 * @author 刘强
 * @version 2026/01/06 14:51
 **/
public class CandidateScoreBo {
    public int number;
    public double score;
    public String reason;  // 得分原因

    public CandidateScoreBo(int number, double score, String reason) {
        this.number = number;
        this.score = score;
        this.reason = reason;
    }
}
