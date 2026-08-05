/**
 * 预测/特征结果文件记录（后端 t_predict_file_record 表对应）。
 */
export interface PredictFileRecord {
  /** 主键ID */
  id: number;
  /** 类型 code：RECOMMEND=号码推荐，ANALYSIS=特征预测 */
  type: string;
  /** 类型展示名：号码推荐 / 特征预测 */
  typeName?: string;
  /** 文件名（含后缀） */
  fileName: string;
  /** 文件绝对路径 */
  filePath?: string;
  /** 创建时间（epoch 毫秒） */
  createTime?: number;
}

/** 类型 code 常量 */
export const PREDICT_FILE_TYPE = {
  RECOMMEND: "RECOMMEND",
  ANALYSIS: "ANALYSIS",
} as const;
