package com.dockermanager.application.port.outbound;

/**
 * 告警通知出站端口。由 Infrastructure 层的 DingTalkNotifier 实现。
 */
public interface NotificationPort {
    /**
     * 发送告警通知。
     *
     * @param notifyType    通知类型 (DINGTALK)
     * @param target        目标地址 (WebHook URL)
     * @param secret        加签密钥（可选）
     * @param title         消息标题
     * @param content       消息内容（Markdown 格式）
     * @return 发送结果描述
     */
    String sendNotification(String notifyType, String target, String secret, String title, String content);
}
