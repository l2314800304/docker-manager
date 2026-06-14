package com.dockermanager.domain.port.inbound;

import java.util.List;
import java.util.Map;

/**
 * 认证操作入站端口。定义用户认证和管理操作的业务契约。
 */
public interface AuthenticationPort {

    /** 用户注册 */
    Map<String, Object> register(String username, String password, String nickname);

    /** 用户登录 */
    Map<String, Object> login(String username, String password);

    /** 获取用户资料 */
    Map<String, Object> getProfile(String username);

    /** 修改密码 */
    void changePassword(String username, String oldPassword, String newPassword);

    /** 初始化默认管理员用户 */
    void initDefaultUser();

    /** 获取所有用户列表（管理员） */
    List<Map<String, Object>> listUsers();

    /** 切换用户启用/禁用 */
    void toggleUserEnabled(Long userId);

    /** 删除用户 */
    void deleteUser(Long userId);

    /** 管理员重置密码 */
    void adminResetPassword(Long userId, String newPassword);
}
