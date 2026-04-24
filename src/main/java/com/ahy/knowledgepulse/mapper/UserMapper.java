package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    User findByUsername(String username);

    User findByEmail(String email);

    List<User> searchUsers(@Param("keyword") String keyword,
                           @Param("limit") Integer limit,
                           @Param("excludeUserId") Long excludeUserId);
}
