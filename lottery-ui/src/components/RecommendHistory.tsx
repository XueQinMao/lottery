"use client";

import { ClockCounterClockwise, Eye } from "@phosphor-icons/react";
import type { AdjustHistoryFile } from "@/types/llm-recommend";

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
  files: AdjustHistoryFile[];
  activeFileName: string | null;
  loading?: boolean;
  listError?: string | null;
  disabled?: boolean;
  onSelect: (file: AdjustHistoryFile) => void;
}

export default function RecommendHistory({
  files,
  activeFileName,
  loading = false,
  listError = null,
  disabled = false,
  onSelect,
}: Props) {
  return (
    <aside className="history-panel" aria-label="最近推荐">
      <div className="history-head">
        <h2>
          <ClockCounterClockwise size={16} weight="regular" aria-hidden="true" />
          最近推荐
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
        <p className="muted history-empty">
          暂无服务端推荐文件。生成后会写入后台目录，可点文件名回看。
        </p>
      ) : (
        <ul className="history-list">
          {files.map((file) => {
            const selected = file.fileName === activeFileName;
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
                    {file.lastModified ? (
                      <time dateTime={new Date(file.lastModified).toISOString()}>
                        {formatTime(file.lastModified)}
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
