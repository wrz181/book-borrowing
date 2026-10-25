package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.service.SysUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 系统用户业务实现：BCrypt 密码校验。
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    private final BCryptPasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public SysUser login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("账号和密码不能为空");
        }
        SysUser user = sysUserMapper.selectByUsername(username.trim());
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        return user;
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }
}
