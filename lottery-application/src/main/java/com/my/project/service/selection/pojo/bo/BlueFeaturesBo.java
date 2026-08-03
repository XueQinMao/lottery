package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * BlueFeaturesBo
 *
 * @author 刘强
 * @version 2025/12/22 19:38
 **/
@Data
public class BlueFeaturesBo {
    private boolean isBig;          // 是否大号 (>8)
    private boolean isOdd;          // 是否奇数
    private int hotness;            // 热度（历史出现次数）
    private int recentGap;          // 距离上次出现的期数
}
