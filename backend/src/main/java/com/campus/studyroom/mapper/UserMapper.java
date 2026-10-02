package com.campus.studyroom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.studyroom.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 用户行锁（评审 #21 并发修复）：checkUserOverlap 前锁定用户行，
     * 串行化同一用户的并发预约创建，防止"同用户同区间、不同座位"并发双双通过检查
     */
    @Select("SELECT * FROM user WHERE id = #{id} FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
