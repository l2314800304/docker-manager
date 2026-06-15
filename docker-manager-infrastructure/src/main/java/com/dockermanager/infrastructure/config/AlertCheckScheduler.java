package com.dockermanager.infrastructure.config;

import com.dockermanager.application.port.outbound.AlertRepositoryPort;
import com.dockermanager.application.port.outbound.HostMetricsPort;
import com.dockermanager.application.port.outbound.HostRepositoryPort;
import com.dockermanager.application.port.outbound.NotificationPort;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.entity.AlertRecord;
import com.dockermanager.domain.entity.AlertRule;
import com.dockermanager.domain.entity.DockerHost;
import com.dockermanager.domain.enums.CompareOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警检查定时任务。
 *
 * <p>每隔 60 秒扫描所有启用的告警规则，采集对应宿主机的指标数据，
 * 判断是否触发告警条件。触发时发送通知并记录告警事件。</p>
 *
 * <h3>告警判断流程：</h3>
 * <ol>
 *   <li>获取所有 enabled 的告警规则</li>
 *   <li>对每条规则，采集关联宿主机的指标</li>
 *   <li>用 CompareOperator 比较实际值与阈值</li>
 *   <li>超过阈值且不在冷却期内 → 发送通知 + 记录</li>
 * </ol>
 */
@Component
public class AlertCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertCheckScheduler.class);

    private final AlertRepositoryPort alertRepository;
    private final HostRepositoryPort hostRepository;
    private final HostMetricsPort hostMetrics;
    private final NotificationPort notificationPort;

    public AlertCheckScheduler(AlertRepositoryPort alertRepository,
                                HostRepositoryPort hostRepository,
                                HostMetricsPort hostMetrics,
                                NotificationPort notificationPort) {
        this.alertRepository = alertRepository;
        this.hostRepository = hostRepository;
        this.hostMetrics = hostMetrics;
        this.notificationPort = notificationPort;
    }

    /**
     * 每 60 秒执行一次告警检查。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void checkAlerts() {
        List<AlertRule> rules = alertRepository.findEnabledRules();
        if (rules.isEmpty()) return;

        log.debug("Checking {} alert rules...", rules.size());

        for (AlertRule rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.warn("Error checking alert rule [{}]: {}", rule.getName(), e.getMessage());
            }
        }
    }

    private void checkRule(AlertRule rule) {
        // 检查冷却期
        if (rule.getLastTriggeredAt() != null) {
            long secondsSinceLastTrigger = java.time.Duration.between(rule.getLastTriggeredAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceLastTrigger < rule.getCooldownSeconds()) return;
        }

        // 采集指标
        HostMetricsDTO metrics;
        if (rule.getHostId() != null) {
            DockerHost host = hostRepository.findById(rule.getHostId()).orElse(null);
            if (host == null || !host.isEnabled()) return;
            try {
                metrics = hostMetrics.collectMetrics(host);
            } catch (Exception e) {
                log.debug("Cannot collect metrics for host {}: {}", host.getName(), e.getMessage());
                return;
            }
        } else {
            // 全局规则：检查所有宿主机
            List<DockerHost> hosts = hostRepository.findByEnabled(true);
            for (DockerHost host : hosts) {
                try {
                    metrics = hostMetrics.collectMetrics(host);
                    evaluateAndNotify(rule, metrics, host);
                } catch (Exception ignored) {}
            }
            return;
        }

        DockerHost host = hostRepository.findById(rule.getHostId()).orElse(null);
        evaluateAndNotify(rule, metrics, host);
    }

    private void evaluateAndNotify(AlertRule rule, HostMetricsDTO metrics, DockerHost host) {
        double value = extractMetricValue(rule.getMetricType(), metrics);

        CompareOperator operator;
        try { operator = CompareOperator.valueOf(rule.getCompareOperator()); }
        catch (Exception e) { return; }

        if (!operator.compare(value, rule.getThreshold())) return;

        // 触发告警
        String hostName = host != null ? host.getName() : "所有宿主机";
        String message = String.format("⚠️ **%s** 告警\n\n" +
                        "- 宿主机: %s\n" +
                        "- 指标: %s\n" +
                        "- 当前值: %.2f\n" +
                        "- 阈值: %s %.2f\n" +
                        "- 时间: %s",
                rule.getName(), hostName, rule.getMetricType(),
                value, operator.getSymbol(), rule.getThreshold(),
                LocalDateTime.now().toString());

        log.info("Alert triggered: rule={}, host={}, metric={}, value={}, threshold={}",
                rule.getName(), hostName, rule.getMetricType(), value, rule.getThreshold());

        // 发送通知
        String result;
        try {
            result = notificationPort.sendNotification(rule.getNotifyType(), rule.getNotifyTarget(),
                    rule.getDingtalkSecret(), "Docker Manager 告警: " + rule.getName(), message);
        } catch (Exception e) {
            result = "发送异常: " + e.getMessage();
        }

        // 记录告警
        AlertRecord record = AlertRecord.builder()
                .ruleId(rule.getId()).ruleName(rule.getName())
                .hostId(host != null ? host.getId() : null)
                .hostName(hostName).metricType(rule.getMetricType())
                .metricValue(value).threshold(rule.getThreshold())
                .message(message).notifyStatus(result.contains("成功") ? "SENT" : "FAILED")
                .notifyResult(result).build();
        alertRepository.saveRecord(record);

        // 更新规则的最后触发时间
        rule.setLastTriggeredAt(LocalDateTime.now());
        alertRepository.saveRule(rule);
    }

    /**
     * 从 HostMetricsDTO 中提取指定指标类型的值。
     */
    private double extractMetricValue(String metricType, HostMetricsDTO metrics) {
        return switch (metricType) {
            case "HOST_CPU" -> metrics.getCpuPercent();
            case "HOST_MEMORY" -> metrics.getMemoryPercent();
            case "HOST_DISK" -> metrics.getDiskPercent();
            default -> 0;
        };
    }
}
