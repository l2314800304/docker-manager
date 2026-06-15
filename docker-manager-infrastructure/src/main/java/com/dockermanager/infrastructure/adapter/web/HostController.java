package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.DockerHostDTO;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.port.inbound.HostOperationPort;
import com.dockermanager.domain.util.ParamValidator;
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
            String name = ParamValidator.getStringOrDefault(body, "name", null);
            String connectionType = ParamValidator.getStringOrDefault(body, "connectionType", "SOCKET");
            String connectionUrl = ParamValidator.getStringOrDefault(body, "connectionUrl", null);
            String certPath = ParamValidator.getStringOrDefault(body, "certPath", null);
            
            ParamValidator.requireNotBlank(name, "主机名称不能为空");
            ParamValidator.requireMaxLength(name, 100, "主机名称最长100个字符");
            if (!"SOCKET".equals(connectionType) && !"TCP".equals(connectionType)) {
                throw new IllegalArgumentException("连接类型必须是 SOCKET 或 TCP");
            }
            ParamValidator.requireNotBlank(connectionUrl, "连接地址不能为空");
            ParamValidator.requireMaxLength(connectionUrl, 500, "连接地址最长500个字符");
            ParamValidator.requireMaxLength(certPath, 500, "证书路径最长500个字符");
            
            DockerHostDTO dto = hostOperationPort.addHost(
                    name, connectionType, connectionUrl,
                    Boolean.TRUE.equals(body.get("tlsEnabled")), certPath);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DockerHostDTO> updateHost(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String name = ParamValidator.getStringOrDefault(body, "name", null);
            String connectionType = ParamValidator.getStringOrDefault(body, "connectionType", "SOCKET");
            String connectionUrl = ParamValidator.getStringOrDefault(body, "connectionUrl", null);
            String certPath = ParamValidator.getStringOrDefault(body, "certPath", null);
            
            ParamValidator.requireNotBlank(name, "主机名称不能为空");
            ParamValidator.requireMaxLength(name, 100, "主机名称最长100个字符");
            if (!"SOCKET".equals(connectionType) && !"TCP".equals(connectionType)) {
                throw new IllegalArgumentException("连接类型必须是 SOCKET 或 TCP");
            }
            ParamValidator.requireNotBlank(connectionUrl, "连接地址不能为空");
            ParamValidator.requireMaxLength(connectionUrl, 500, "连接地址最长500个字符");
            ParamValidator.requireMaxLength(certPath, 500, "证书路径最长500个字符");
            
            DockerHostDTO dto = hostOperationPort.updateHost(id,
                    name, connectionType, connectionUrl,
                    Boolean.TRUE.equals(body.get("tlsEnabled")), certPath,
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
