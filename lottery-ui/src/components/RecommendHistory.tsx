"use client";

import { ClockCounterClockwise, Eye } from "@phosphor-icons/react";
import type { PredictFileRecord } from "@/types/predict-file-record";

function formatTime(ts?: number) {
  if (!ts) return "";
  const d = new Date(ts);
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  const pad = (n: number) => String(n).padStart(2, "0");
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  if (sameDay) return `今天 ${hm}`;
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`;
}

function displayName(fileName: string) {
  return fileName.replace(/\.json$/i, "");
}

interface Props {
  /** 历史记录列表 */
  files: PredictFileRecord[];
  /** 当前选中的文件名 */
  activeFileName: string | null;
  /** 面板标题，默认「最近推荐」 */
  title?: string;
  /** 面板 aria-label */
  ariaLabel?: string;
  /** 空态文案 */
  emptyHint?: string;
  loading?: boolean;
  listError?: string | null;
  disabled?: boolean;
  onSelect: (file: PredictFileRecord) => void;
}

export default function RecommendHistory({
  files,
  activeFileName,
  title = "最近推荐",
  ariaLabel = "最近推荐",
  emptyHint = "暂无历史记录。生成后会写入后台目录，可点文件名回看。",
  loading = false,
  listError = null,
  disabled = false,
  onSelect,
}: Props) {
  return (
    <aside className="history-panel" aria-label={ariaLabel}>
      <div className="history-head">
        <h2>
          <ClockCounterClockwise size={16} weight="regular" aria-hidden="true" />
          {title}
        </h2>
      </div>
      {loading ? (
        <p className="muted history-empty" role="status">
          正在加载文件列表...
        </p>
      ) : listError ? (
        <p className="status error" role="alert">
          {listError}
        </p>
      ) : files.length === 0 ? (
        <p className="muted history-empty">{emptyHint}</p>
      ) : (
        <ul className="history-list">
          {files.map((file) => {
            const selected = file.fileName === activeFileName;
            // 用 fileName 做 key：雪花 Long 超过 JS Number 安全范围会精度丢失导致 key 冲突，
            // fileName 带时间戳业务上唯一
            return (
              <li key={file.fileName}>
                <button
                  type="button"
                  className={`history-item ${selected ? "active" : ""}`}
                  onClick={() => onSelect(file)}
                  disabled={disabled}
                  aria-pressed={selected}
                  aria-current={selected ? "true" : undefined}
                >
                  <div className="history-item-meta">
                    {file.createTime ? (
                      <time dateTime={new Date(file.createTime).toISOString()}>
                        {formatTime(file.createTime)}
                      </time>
                    ) : (
                      <span>未知时间</span>
                    )}
                  </div>
                  <p className="history-file-name" title={file.fileName}>
                    {displayName(file.fileName)}
                  </p>
                  <span className="history-action">
                    <Eye size={14} weight={selected ? "fill" : "regular"} aria-hidden="true" />
                    {selected ? "当前回看" : "回看"}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </aside>
  );
}
