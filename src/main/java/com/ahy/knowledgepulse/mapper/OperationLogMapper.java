package com.ahy.knowledgepulse.mapper;

import com.ahy.knowledgepulse.entity.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    List<OperationLog> findByUserId(Long userId);

    List<OperationLog> findByModule(String module);
}
