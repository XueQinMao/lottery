package com.my.project.application.pojo;

public class RuleStat {

    private final String ruleName;
    private long total;          // 参与统计期数
    private long emptyCount;     // 返回空的次数
    private long wrongKill;      // 错杀次数（杀中的号在下一期开出）
    private long rightKill;      // 正确杀号次数
    private long killedTotal;    // 累计杀号个数（用于算平均）

    public RuleStat(String ruleName) {
        this.ruleName = ruleName;
    }

    public void record(boolean empty, int killedSize, int hitCount) {
        total++;
        if (empty) {
            emptyCount++;
        }
        killedTotal += killedSize;
        if (hitCount > 0) {
            wrongKill++;
        } else {
            rightKill++;
        }
    }

    public String getRuleName() { return ruleName; }
    public long getTotal() { return total; }
    public long getEmptyCount() { return emptyCount; }
    public long getWrongKill() { return wrongKill; }
    public long getRightKill() { return rightKill; }
    public long getKilledTotal() { return killedTotal; }

    public double getWrongKillRate() {
        return total == 0 ? 0 : (double) wrongKill / total;
    }

    public double getEmptyRate() {
        return total == 0 ? 0 : (double) emptyCount / total;
    }

    public double getAvgKill() {
        return total == 0 ? 0 : (double) killedTotal / total;
    }

    public String riskLevel() {
        double r = getWrongKillRate();
        if (r >= 0.30) return "高风险";
        if (r >= 0.20) return "中风险";
        if (r >= 0.10) return "低风险";
        return "较安全";
    }

    @Override
    public String toString() {
        return String.format(
            "%-22s 期数=%-4d 空=%-4d 空率=%.2f%% 错杀=%-4d 错杀率=%.2f%% 正确=%-4d 均杀=%.2f %s",
            ruleName, total, emptyCount, getEmptyRate() * 100,
            wrongKill, getWrongKillRate() * 100,
            rightKill, getAvgKill(), riskLevel());
    }
}
