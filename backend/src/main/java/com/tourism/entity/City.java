package com.tourism.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("city")
public class City {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long provinceId;
    private String name;
    private String adcode;      // 高德城市区划码(天气查询用)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProvinceId() { return provinceId; }
    public void setProvinceId(Long provinceId) { this.provinceId = provinceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }
}
