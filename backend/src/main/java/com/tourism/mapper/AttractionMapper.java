package com.tourism.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tourism.entity.Attraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AttractionMapper extends BaseMapper<Attraction> {

    @Update("UPDATE attraction SET views = views + 1 WHERE id = #{id}")
    int incrementViews(Long id);
}
