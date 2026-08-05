export interface ColdHotTrendVo {
  periods: string[];
  /** 每期开出红球中属于"热号"的个数 */
  redHotCounts: number[];
  /** 每期开出红球中属于"温号"的个数 */
  redWarmCounts: number[];
  /** 每期开出红球中属于"冷号"的个数 */
  redColdCounts: number[];
  redHotAvg: number;
  redWarmAvg: number;
  redColdAvg: number;
  /** 每期开出蓝球所属档位：0=冷, 1=温, 2=热 */
  blueBandFlags: number[];
  /** 每期开出蓝球所属档位文案："冷" / "温" / "热" */
  blueBandLabels: string[];
  /** 蓝球为热号的期数占比 */
  blueHotAvg: number;
  /** 蓝球为温号的期数占比 */
  blueWarmAvg: number;
  /** 蓝球为冷号的期数占比 */
  blueColdAvg: number;
  /** 分类依据说明 */
  basis: string;
}
