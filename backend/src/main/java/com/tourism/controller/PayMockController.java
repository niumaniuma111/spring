package com.tourism.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;

/**
 * 模拟支付二维码（演示）：生成一张真实可扫的二维码，
 * 扫码内容为订单信息的纯文本——逼真还原扫码支付环节，不涉及任何真实扣款。
 */
@RestController
@RequestMapping("/api/orders")
public class PayMockController {

    @GetMapping("/qrcode")
    public ResponseEntity<byte[]> qrcode(@RequestParam String orderNo,
                                         @RequestParam(defaultValue = "0") String amount) throws Exception {
        String content = "知行山水·模拟支付(演示)\n订单号:" + orderNo + "\n金额:¥" + amount
                + "\n本二维码为演示用途,扫码不产生真实扣款";
        BitMatrix matrix = new QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, 280, 280);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(out.toByteArray());
    }
}
