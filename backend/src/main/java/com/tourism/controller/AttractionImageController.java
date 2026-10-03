package com.tourism.controller;

import com.tourism.common.Result;
import com.tourism.entity.AttractionImage;
import com.tourism.exception.BizException;
import com.tourism.mapper.AttractionImageMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 景点图片上传与读取：
 * - 上传（管理员）：POST /api/admin/attractions/image，multipart，≤5MB，image/*
 * - 读取（登录用户）：GET /api/img/{id}，直接输出图片二进制
 * 图片存 DB（MEDIUMBLOB）而非容器文件系统：容器重建不丢、多容器无需共享卷。
 */
@RestController
public class AttractionImageController {

    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    private final AttractionImageMapper imageMapper;

    public AttractionImageController(AttractionImageMapper m) {
        this.imageMapper = m;
    }

    @PostMapping("/api/admin/attractions/image")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new BizException("请选择图片文件");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) throw new BizException("仅支持图片文件（jpg/png/webp/gif）");
        if (file.getSize() > MAX_SIZE) throw new BizException("图片大小不能超过 5MB");

        AttractionImage img = new AttractionImage();
        img.setFileName(file.getOriginalFilename());
        img.setContentType(ct);
        img.setData(file.getBytes());
        imageMapper.insert(img);
        // 返回可直接存入 attraction.image 的访问路径
        return Result.ok("/api/img/" + img.getId());
    }

    @GetMapping("/api/img/{id}")
    public ResponseEntity<byte[]> serve(@PathVariable Long id) {
        AttractionImage img = imageMapper.selectById(id);
        if (img == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(img.getContentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)))
                .body(img.getData());
    }
}
