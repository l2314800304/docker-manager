package com.dockermanager.infrastructure.adapter.docker;

import com.dockermanager.application.port.outbound.NotificationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 钉钉 WebHook 通知适配器。实现 NotificationPort 出站端口。
 *
 * <p>通过钉钉自定义机器人的 WebHook 接口发送告警通知。</p>
 *
 * <h3>支持的安全方式：</h3>
 * <ul>
 *   <li><b>无签名</b>: 直接发送，webhookUrl 即完整 URL</li>
 *   <li><b>加签</b>: 使用 HmacSHA256 算法对 timestamp + "\n" + secret 签名，
 *       将签名和时间戳附加到 URL 参数中</li>
 * </ul>
 *
 * <h3>消息格式：</h3>
 * <p>使用钉钉 Markdown 消息类型，支持标题、正文、@人等功能。</p>
 */
@Component
public class DingTalkNotifier implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DingTalkNotifier() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sendNotification(String notifyType, String target, String secret,
                                    String title, String content) {
        if (!"DINGTALK".equalsIgnoreCase(notifyType)) {
            return "不支持的通知类型: " + notifyType;
        }

        try {
            // 构建请求 URL（含签名）
            String url = buildSignedUrl(target, secret);

            // 构建 Markdown 消息体
            Map<String, Object> body = Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("title", title, "text", content),
                    "at", Map.of("isAtAll", false)
            );

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 检查钉钉 API 返回的 errcode
                Map<?, ?> result = objectMapper.readValue(response.body(), Map.class);
                int errcode = result.containsKey("errcode") ? ((Number) result.get("errcode")).intValue() : -1;
                if (errcode == 0) {
                    log.info("DingTalk notification sent successfully: {}", title);
                    return "发送成功";
                } else {
                    String errmsg = result.containsKey("errmsg") ? result.get("errmsg").toString() : "未知错误";
                    log.warn("DingTalk API error: errcode={}, errmsg={}", errcode, errmsg);
                    return "钉钉API错误: " + errmsg;
                }
            } else {
                return "HTTP错误: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("Failed to send DingTalk notification: {}", e.getMessage());
            return "发送失败: " + e.getMessage();
        }
    }

    /**
     * 构建带签名的钉钉 WebHook URL。
     *
     * <p>加签算法：</p>
     * <ol>
     *   <li>timestamp = 当前毫秒时间戳</li>
     *   <li>stringToSign = timestamp + "\n" + secret</li>
     *   <li>sign = Base64(HmacSHA256(stringToSign, secret))</li>
     *   <li>url = webhookUrl + "&timestamp=" + timestamp + "&sign=" + URLEncode(sign)</li>
     * </ol>
     */
    private String buildSignedUrl(String webhookUrl, String secret) {
        if (secret == null || secret.isBlank()) {
            return webhookUrl;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;

            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);

            String separator = webhookUrl.contains("?") ? "&" : "?";
            return webhookUrl + separator + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.warn("Failed to sign DingTalk URL: {}", e.getMessage());
            return webhookUrl;
        }
    }
}
