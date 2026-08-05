package com.my.project.service.history.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 每期开奖号码中"冷/温/热"号个数趋势。
 * <p>
 * 对每期开奖，取其前 30 期作为样本调用 {@code ColdHotAnalysisUtils.calculate} 得到冷温热号清单，
 * 再统计本期实际开出的红球/蓝球落入各档的个数，按期号升序（最旧 → 最新）输出。
 *
 * <ul>
 *     <li>红球：每期 6 个，redHotCounts[i] + redWarmCounts[i] + redColdCounts[i] ≡ 6（样本不足或未分类时可能小于 6）</li>
 *     <li>蓝球：每期 1 个，blueBandFlags[i] ∈ {0=冷, 1=温, 2=热}，blueBandLabels[i] 为文案</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/09/21
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColdHotTrendVo {

    /** 期号列表（最旧 → 最新） */
    private List<String> periods;

    /** 每期开出红球中属于"热号"的个数 */
    private List<Integer> redHotCounts;

    /** 每期开出红球中属于"温号"的个数 */
    private List<Integer> redWarmCounts;

    /** 每期开出红球中属于"冷号"的个数 */
    private List<Integer> redColdCounts;

    /** 红球热号个数均值 */
    private Double redHotAvg;

    /** 红球温号个数均值 */
    private Double redWarmAvg;

    /** 红球冷号个数均值 */
    private Double redColdAvg;

    /** 每期开出蓝球所属档位：0=冷, 1=温, 2=热 */
    private List<Integer> blueBandFlags;

    /** 每期开出蓝球所属档位文案："冷" / "温" / "热" */
    private List<String> blueBandLabels;

    /** 蓝球为热号的期数占比 */
    private Double blueHotAvg;

    /** 蓝球为温号的期数占比 */
    private Double blueWarmAvg;

    /** 蓝球为冷号的期数占比 */
    private Double blueColdAvg;

    /** 分类依据说明（来自 ColdHotAnalysisUtils） */
    private String basis;
}
