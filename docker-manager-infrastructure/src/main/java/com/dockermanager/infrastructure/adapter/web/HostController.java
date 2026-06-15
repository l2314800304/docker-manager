package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.DockerHostDTO;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.port.inbound.HostOperationPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 宿主机管理 REST API 控制器 */
@RestController
@RequestMapping("/api/hosts")
public class HostController {
    private final HostOperationPort hostOperationPort;
    public HostController(HostOperationPort hostOperationPort) { this.hostOperationPort = hostOperationPort; }

    @GetMapping
    public List<DockerHostDTO> listHosts() { return hostOperationPort.listHosts(); }

    @GetMapping("/{id}")
    public ResponseEntity<DockerHostDTO> getHost(@PathVariable Long id) {
        return hostOperationPort.getHost(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DockerHostDTO> addHost(@RequestBody Map<String, Object> body) {
        try {
            DockerHostDTO dto = hostOperationPort.addHost(
                    (String) body.get("name"),
                    (String) body.getOrDefault("connectionType", "SOCKET"),
                    (String) body.get("connectionUrl"),
                    Boolean.TRUE.equals(body.get("tlsEnabled")),
                    (String) body.get("certPath"));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DockerHostDTO> updateHost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            DockerHostDTO dto = hostOperationPort.updateHost(id,
                    (String) body.get("name"),
                    (String) body.getOrDefault("connectionType", "SOCKET"),
                    (String) body.get("connectionUrl"),
                    Boolean.TRUE.equals(body.get("tlsEnabled")),
                    (String) body.get("certPath"),
                    !Boolean.FALSE.equals(body.get("enabled")));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteHost(@PathVariable Long id) {
        try {
            hostOperationPort.deleteHost(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        try {
            boolean ok = hostOperationPort.testConnection(id);
            return ResponseEntity.ok(Map.of("success", ok, "message", ok ? "连接成功" : "连接失败"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "连接异常: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<HostMetricsDTO> getMetrics(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(hostOperationPort.getHostMetrics(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/metrics")
    public List<HostMetricsDTO> getAllMetrics() { return hostOperationPort.getAllHostMetrics(); }
}
