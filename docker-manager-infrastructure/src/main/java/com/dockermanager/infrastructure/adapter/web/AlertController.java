package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.AlertRecordDTO;
import com.dockermanager.domain.dto.AlertRuleDTO;
import com.dockermanager.domain.port.inbound.AlertManagementPort;
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
        AlertRuleDTO dto = alertManagementPort.addRule(
                (String) body.get("name"),
                body.containsKey("hostId") ? ((Number) body.get("hostId")).longValue() : null,
                (String) body.get("metricType"),
                ((Number) body.get("threshold")).doubleValue(),
                (String) body.getOrDefault("compareOperator", "GT"),
                body.containsKey("durationSeconds") ? ((Number) body.get("durationSeconds")).intValue() : 0,
                (String) body.getOrDefault("notifyType", "DINGTALK"),
                (String) body.get("notifyTarget"),
                (String) body.get("dingtalkSecret"),
                body.containsKey("cooldownSeconds") ? ((Number) body.get("cooldownSeconds")).intValue() : 300);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<AlertRuleDTO> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            AlertRuleDTO dto = alertManagementPort.updateRule(id,
                    (String) body.get("name"),
                    body.containsKey("hostId") ? ((Number) body.get("hostId")).longValue() : null,
                    (String) body.get("metricType"),
                    ((Number) body.get("threshold")).doubleValue(),
                    (String) body.getOrDefault("compareOperator", "GT"),
                    body.containsKey("durationSeconds") ? ((Number) body.get("durationSeconds")).intValue() : 0,
                    (String) body.getOrDefault("notifyType", "DINGTALK"),
                    (String) body.get("notifyTarget"),
                    (String) body.get("dingtalkSecret"),
                    !Boolean.FALSE.equals(body.get("enabled")),
                    body.containsKey("cooldownSeconds") ? ((Number) body.get("cooldownSeconds")).intValue() : 300);
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
        return alertManagementPort.listRecords(Math.min(limit, 200));
    }

    @GetMapping("/records/rule/{ruleId}")
    public List<AlertRecordDTO> listRecordsByRule(@PathVariable Long ruleId, @RequestParam(defaultValue = "50") int limit) {
        return alertManagementPort.listRecordsByRule(ruleId, Math.min(limit, 200));
    }

    // ===== 通知测试 =====
    @PostMapping("/test-notification")
    public ResponseEntity<Map<String, String>> testNotification(@RequestBody Map<String, String> body) {
        String result = alertManagementPort.testNotification(
                body.getOrDefault("notifyType", "DINGTALK"),
                body.get("notifyTarget"),
                body.get("dingtalkSecret"));
        return ResponseEntity.ok(Map.of("result", result));
    }
}
