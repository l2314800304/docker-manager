package com.dockermanager.domain.enums;

/** 告警规则比较运算符枚举 */
public enum CompareOperator {
    GT(">", "大于"), GTE(">=", "大于等于"), LT("<", "小于"), LTE("<=", "小于等于"), EQ("==", "等于");

    private final String symbol;
    private final String displayName;
    CompareOperator(String symbol, String displayName) { this.symbol = symbol; this.displayName = displayName; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }

    /** 执行比较: value [operator] threshold */
    public boolean compare(double value, double threshold) {
        return switch (this) {
            case GT -> value > threshold;
            case GTE -> value >= threshold;
            case LT -> value < threshold;
            case LTE -> value <= threshold;
            case EQ -> Double.compare(value, threshold) == 0;
        };
    }
}
