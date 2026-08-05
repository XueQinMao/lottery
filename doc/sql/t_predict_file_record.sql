-- =============================================================================
-- 预测/特征结果文件记录表
--
-- 统一存储「号码推荐」与「特征预测」两类落盘文件的元数据，
-- 供前端历史列表查询与回看使用。
--
-- 数据库：MariaDB / MySQL
-- 主键：雪花算法（应用侧 IdType.ASSIGN_ID 生成 BIGINT，非自增）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `t_predict_file_record`
(
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法，应用侧生成）',
    `type`        VARCHAR(32)  NOT NULL COMMENT '类型：RECOMMEND=号码推荐，ANALYSIS=特征预测',
    `file_name`   VARCHAR(255) NOT NULL COMMENT '文件名（含后缀）',
    `file_path`   VARCHAR(512) NOT NULL COMMENT '文件绝对路径',
    `create_time` DATETIME     NULL DEFAULT NULL COMMENT '创建时间（应用侧 MybatisMetaObjectHandler 自动填充）',
    PRIMARY KEY (`id`),
    KEY `idx_type_create_time` (`type`, `create_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='预测/特征结果文件记录表';

-- =============================================================================
-- 存量文件初始化（create_time 取自文件修改时间）
-- id 为手工占位雪花 ID，避免与后续应用侧 ASSIGN_ID 冲突请勿重复执行
-- =============================================================================

INSERT INTO `t_predict_file_record` (`id`, `type`, `file_name`, `file_path`, `create_time`)
VALUES
    (1970123456000000001, 'ANALYSIS',  'feature.analysis.cache2026104100.json',     'D:\\home\\cache\\llm\\feature.analysis.cache2026104100.json',     '2026-09-10 16:13:26'),
    (1970123456000000002, 'RECOMMEND', 'adjust_20260910161937_c2_topNfalse.json',   'D:\\home\\python\\adjust\\adjust_20260910161937_c2_topNfalse.json', '2026-09-10 16:19:37'),
    (1970123456000000003, 'RECOMMEND', 'adjust_20260910165336_c2_topNfalse.json',   'D:\\home\\python\\adjust\\adjust_20260910165336_c2_topNfalse.json', '2026-09-10 16:53:36'),
    (1970123456000000004, 'RECOMMEND', 'adjust_20260910192511_c2_topNfalse.json',   'D:\\home\\python\\adjust\\adjust_20260910192511_c2_topNfalse.json', '2026-09-10 19:25:11');
