package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.AlertRecordDTO;
import com.dockermanager.domain.dto.AlertRuleDTO;
import com.dockermanager.domain.port.inbound.AlertManagementPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 告警管理 REST API 控制器 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertManagementPort alertManagementPort;
    public AlertController(AlertManagementPort alertManagementPort) { this.alertManagementPort = alertManagementPort; }

    // ===== 规则管理 =====
    @GetMapping("/rules")
    public List<AlertRuleDTO> listRules() { return alertManagementPort.listRules(); }

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleDTO> addRule(@RequestBody Map<String, Object> body) {
        String name = ParamValidator.getStringOrDefault(body, "name", null);
        Long hostId = ParamValidator.getLongOrNull(body, "hostId");
        String metricType = ParamValidator.getStringOrDefault(body, "metricType", null);
        double threshold = ParamValidator.getDoubleOrDefault(body, "threshold", 0.0);
        String compareOperator = ParamValidator.getStringOrDefault(body, "compareOperator", "GT");
        int durationSeconds = ParamValidator.getIntOrDefault(body, "durationSeconds", 0);
        String notifyType = ParamValidator.getStringOrDefault(body, "notifyType", "DINGTALK");
        String notifyTarget = ParamValidator.getStringOrDefault(body, "notifyTarget", null);
        String dingtalkSecret = ParamValidator.getStringOrDefault(body, "dingtalkSecret", null);
        int cooldownSeconds = ParamValidator.getIntOrDefault(body, "cooldownSeconds", 300);
        
        ParamValidator.requireNotBlank(name, "规则名称不能为空");
        ParamValidator.requireMaxLength(name, 100, "规则名称最长100个字符");
        ParamValidator.requireEnumValue(metricType, com.dockermanager.domain.enums.AlertMetricType.class, "无效的指标类型");
        ParamValidator.requireInRange(threshold, 0.0, 100.0, "阈值必须在0-100之间");
        ParamValidator.requireEnumValue(compareOperator, com.dockermanager.domain.enums.CompareOperator.class, "无效的比较操作符");
        ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
        
        AlertRuleDTO dto = alertManagementPort.addRule(
                name, hostId, metricType, threshold, compareOperator,
                durationSeconds, notifyType, notifyTarget, dingtalkSecret, cooldownSeconds);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<AlertRuleDTO> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String name = ParamValidator.getStringOrDefault(body, "name", null);
            Long hostId = ParamValidator.getLongOrNull(body, "hostId");
            String metricType = ParamValidator.getStringOrDefault(body, "metricType", null);
            double threshold = ParamValidator.getDoubleOrDefault(body, "threshold", 0.0);
            String compareOperator = ParamValidator.getStringOrDefault(body, "compareOperator", "GT");
            int durationSeconds = ParamValidator.getIntOrDefault(body, "durationSeconds", 0);
            String notifyType = ParamValidator.getStringOrDefault(body, "notifyType", "DINGTALK");
            String notifyTarget = ParamValidator.getStringOrDefault(body, "notifyTarget", null);
            String dingtalkSecret = ParamValidator.getStringOrDefault(body, "dingtalkSecret", null);
            boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
            int cooldownSeconds = ParamValidator.getIntOrDefault(body, "cooldownSeconds", 300);
            
            ParamValidator.requireNotBlank(name, "规则名称不能为空");
            ParamValidator.requireMaxLength(name, 100, "规则名称最长100个字符");
            ParamValidator.requireEnumValue(metricType, com.dockermanager.domain.enums.AlertMetricType.class, "无效的指标类型");
            ParamValidator.requireInRange(threshold, 0.0, 100.0, "阈值必须在0-100之间");
            ParamValidator.requireEnumValue(compareOperator, com.dockermanager.domain.enums.CompareOperator.class, "无效的比较操作符");
            ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
            
            AlertRuleDTO dto = alertManagementPort.updateRule(id,
                    name, hostId, metricType, threshold, compareOperator,
                    durationSeconds, notifyType, notifyTarget, dingtalkSecret,
                    enabled, cooldownSeconds);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, String>> deleteRule(@PathVariable Long id) {
        alertManagementPort.deleteRule(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    // ===== 告警记录 =====
    @GetMapping("/records")
    public List<AlertRecordDTO> listRecords(@RequestParam(defaultValue = "50") int limit) {
        return alertManagementPort.listRecords(ParamValidator.normalizeLimit(limit, 200));
    }

    @GetMapping("/records/rule/{ruleId}")
    public List<AlertRecordDTO> listRecordsByRule(@PathVariable Long ruleId, @RequestParam(defaultValue = "50") int limit) {
        return alertManagementPort.listRecordsByRule(ruleId, ParamValidator.normalizeLimit(limit, 200));
    }

    // ===== 通知测试 =====
    @PostMapping("/test-notification")
    public ResponseEntity<Map<String, String>> testNotification(@RequestBody Map<String, String> body) {
        String notifyType = ParamValidator.getStringOrDefault(body, "notifyType", "DINGTALK");
        String notifyTarget = ParamValidator.getStringOrDefault(body, "notifyTarget", null);
        String dingtalkSecret = ParamValidator.getStringOrDefault(body, "dingtalkSecret", null);
        
        ParamValidator.requireNotBlank(notifyTarget, "通知目标不能为空");
        
        String result = alertManagementPort.testNotification(notifyType, notifyTarget, dingtalkSecret);
        return ResponseEntity.ok(Map.of("result", result));
    }
}
