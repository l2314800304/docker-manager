package com.dockermanager.application.service;

import com.dockermanager.application.port.outbound.AlertRepositoryPort;
import com.dockermanager.application.port.outbound.NotificationPort;
import com.dockermanager.domain.dto.AlertRecordDTO;
import com.dockermanager.domain.dto.AlertRuleDTO;
import com.dockermanager.domain.entity.AlertRecord;
import com.dockermanager.domain.entity.AlertRule;
import com.dockermanager.domain.enums.AlertMetricType;
import com.dockermanager.domain.enums.CompareOperator;
import com.dockermanager.domain.port.inbound.AlertManagementPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警管理应用服务。实现 AlertManagementPort 入站端口。
 * 编排告警规则 CRUD、告警记录查询和通知测试。
 */
@Service
public class AlertAppService implements AlertManagementPort {

    private final AlertRepositoryPort alertRepository;
    private final NotificationPort notificationPort;

    public AlertAppService(AlertRepositoryPort alertRepository, NotificationPort notificationPort) {
        this.alertRepository = alertRepository;
        this.notificationPort = notificationPort;
    }

    @Override
    public List<AlertRuleDTO> listRules() {
        return alertRepository.findAllRules().stream().map(this::toRuleDTO).collect(Collectors.toList());
    }

    @Override
    public AlertRuleDTO addRule(String name, Long hostId, String metricType, double threshold,
                                 String compareOperator, int durationSeconds, String notifyType,
                                 String notifyTarget, String dingtalkSecret, int cooldownSeconds) {
        ParamValidator.requireLength(name, 1, 100, "规则名称长度需在1-100之间");
        ParamValidator.requireEnumValue(metricType, AlertMetricType.class, "无效的指标类型");
        ParamValidator.requireInRange(threshold, 0.0, 100.0, "阈值必须在0-100之间");
        ParamValidator.requireEnumValue(compareOperator, CompareOperator.class, "无效的比较操作符");
        if (!"DINGTALK".equals(notifyType)) {
            throw new IllegalArgumentException("通知类型必须是 DINGTALK");
        }
        ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
        ParamValidator.requireMaxLength(notifyTarget, 500, "通知目标最长500个字符");
        
        AlertRule rule = AlertRule.builder()
                .name(name).hostId(hostId).metricType(metricType).threshold(threshold)
                .compareOperator(compareOperator).durationSeconds(durationSeconds)
                .notifyType(notifyType).notifyTarget(notifyTarget).dingtalkSecret(dingtalkSecret)
                .cooldownSeconds(cooldownSeconds).enabled(true).build();
        return toRuleDTO(alertRepository.saveRule(rule));
    }

    @Override
    public AlertRuleDTO updateRule(Long ruleId, String name, Long hostId, String metricType,
                                    double threshold, String compareOperator, int durationSeconds,
                                    String notifyType, String notifyTarget, String dingtalkSecret,
                                    boolean enabled, int cooldownSeconds) {
        ParamValidator.requireLength(name, 1, 100, "规则名称长度需在1-100之间");
        ParamValidator.requireEnumValue(metricType, AlertMetricType.class, "无效的指标类型");
        ParamValidator.requireInRange(threshold, 0.0, 100.0, "阈值必须在0-100之间");
        ParamValidator.requireEnumValue(compareOperator, CompareOperator.class, "无效的比较操作符");
        if (!"DINGTALK".equals(notifyType)) {
            throw new IllegalArgumentException("通知类型必须是 DINGTALK");
        }
        ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
        ParamValidator.requireMaxLength(notifyTarget, 500, "通知目标最长500个字符");
        
        AlertRule rule = alertRepository.findRuleById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("告警规则不存在"));
        rule.setName(name); rule.setHostId(hostId); rule.setMetricType(metricType);
        rule.setThreshold(threshold); rule.setCompareOperator(compareOperator);
        rule.setDurationSeconds(durationSeconds); rule.setNotifyType(notifyType);
        rule.setNotifyTarget(notifyTarget); rule.setDingtalkSecret(dingtalkSecret);
        rule.setEnabled(enabled); rule.setCooldownSeconds(cooldownSeconds);
        return toRuleDTO(alertRepository.saveRule(rule));
    }

    @Override
    public void deleteRule(Long ruleId) {
        alertRepository.deleteRuleById(ruleId);
    }

    @Override
    public List<AlertRecordDTO> listRecords(int limit) {
        return alertRepository.findRecentRecords(limit).stream().map(this::toRecordDTO).collect(Collectors.toList());
    }

    @Override
    public List<AlertRecordDTO> listRecordsByRule(Long ruleId, int limit) {
        return alertRepository.findRecordsByRuleId(ruleId, limit).stream().map(this::toRecordDTO).collect(Collectors.toList());
    }

    @Override
    public String testNotification(String notifyType, String notifyTarget, String dingtalkSecret) {
        ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
        return notificationPort.sendNotification(notifyType, notifyTarget, dingtalkSecret,
                "Docker Manager 测试通知", "## ✅ 通知测试成功\n\n这是一条来自 Docker Manager 的测试消息。\n\n> 如果您收到此消息，说明 WebHook 配置正确。");
    }

    private AlertRuleDTO toRuleDTO(AlertRule r) {
        return AlertRuleDTO.builder()
                .id(r.getId()).name(r.getName()).hostId(r.getHostId()).metricType(r.getMetricType())
                .threshold(r.getThreshold()).compareOperator(r.getCompareOperator())
                .durationSeconds(r.getDurationSeconds()).notifyType(r.getNotifyType())
                .notifyTarget(r.getNotifyTarget()).enabled(r.isEnabled())
                .cooldownSeconds(r.getCooldownSeconds())
                .lastTriggeredAt(r.getLastTriggeredAt() != null ? r.getLastTriggeredAt().toString() : null).build();
    }

    private AlertRecordDTO toRecordDTO(AlertRecord r) {
        return AlertRecordDTO.builder()
                .id(r.getId()).ruleId(r.getRuleId()).ruleName(r.getRuleName())
                .hostId(r.getHostId()).hostName(r.getHostName()).metricType(r.getMetricType())
                .metricValue(r.getMetricValue()).threshold(r.getThreshold()).message(r.getMessage())
                .notifyStatus(r.getNotifyStatus()).notifyResult(r.getNotifyResult())
                .triggeredAt(r.getTriggeredAt() != null ? r.getTriggeredAt().toString() : null).build();
    }
}
