package com.my.project.service.selection.pojo.bo;

import lombok.Data;

/**
 * CategoricalFeaturesBo
 *
 * @author 刘强
 * @version 2025/12/22 19:37
 **/
@Data
public class CategoricalFeaturesBo {
    private String oddEvenPattern;      // 奇偶组合，如"4奇2偶"
    private String bigSmallPattern;     // 大小组合，如"3大3小"
    private String zonePattern;         // 三区分布，如"2-2-2"
}
