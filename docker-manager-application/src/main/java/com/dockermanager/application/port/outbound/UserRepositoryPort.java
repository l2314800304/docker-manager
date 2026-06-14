package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.entity.User;
import java.util.List;
import java.util.Optional;

/**
 * 用户持久化出站端口。定义用户数据的存储和查询契约。
 */
public interface UserRepositoryPort {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    User save(User user);
    List<User> findAll();
    Optional<User> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);
    long count();
}
