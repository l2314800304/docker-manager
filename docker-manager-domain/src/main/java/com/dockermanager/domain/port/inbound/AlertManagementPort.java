package com.dockermanager.domain.port.inbound;

import com.dockermanager.domain.dto.AlertRecordDTO;
import com.dockermanager.domain.dto.AlertRuleDTO;
import java.util.List;

/**
 * 告警管理入站端口。定义告警规则配置和告警记录查询的业务契约。
 */
public interface AlertManagementPort {
    /** 获取所有告警规则 */
    List<AlertRuleDTO> listRules();
    /** 添加告警规则 */
    AlertRuleDTO addRule(String name, Long hostId, String metricType, double threshold,
                         String compareOperator, int durationSeconds, String notifyType,
                         String notifyTarget, String dingtalkSecret, int cooldownSeconds);
    /** 更新告警规则 */
    AlertRuleDTO updateRule(Long ruleId, String name, Long hostId, String metricType,
                            double threshold, String compareOperator, int durationSeconds,
                            String notifyType, String notifyTarget, String dingtalkSecret,
                            boolean enabled, int cooldownSeconds);
    /** 删除告警规则 */
    void deleteRule(Long ruleId);
    /** 获取告警记录 */
    List<AlertRecordDTO> listRecords(int limit);
    /** 按规则获取告警记录 */
    List<AlertRecordDTO> listRecordsByRule(Long ruleId, int limit);
    /** 手动测试告警通知（发送测试消息到钉钉） */
    String testNotification(String notifyType, String notifyTarget, String dingtalkSecret);
}
