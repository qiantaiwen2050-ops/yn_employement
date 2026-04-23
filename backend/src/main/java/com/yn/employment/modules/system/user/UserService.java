package com.yn.employment.modules.system.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper mapper;

    public UserService(UserMapper mapper) { this.mapper = mapper; }

    public User getByUsername(String username) {
        return mapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    public User getById(Long id) {
        return mapper.selectById(id);
    }

    public void save(User u) {
        if (u.getId() == null) mapper.insert(u);
        else mapper.updateById(u);
    }
}
