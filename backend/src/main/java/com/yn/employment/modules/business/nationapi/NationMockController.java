package com.yn.employment.modules.business.nationapi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local-only "国家失业监测系统" simulator (Java-call, not REST). About 10% random
 * failure rate so the retry flow can be demonstrated; small think-time so the
 * UI shows a "正在传输" tick before the result settles.
 */
@Service
public class NationMockController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MockResponse upload(MockRequest req) {
        try { Thread.sleep(150 + ThreadLocalRandom.current().nextInt(150)); } catch (InterruptedException ignored) {}
        if (req.dataCount == null || req.dataCount <= 0)
            return MockResponse.failure("E001", "上报数据为空");
        if (req.periodName == null || req.periodName.isBlank())
            return MockResponse.failure("E002", "缺少调查期名称");
        if (ThreadLocalRandom.current().nextInt(100) < 10)
            return MockResponse.failure("E500", "国家系统繁忙，请稍后重试");
        return MockResponse.success(
                "GJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "已接收 " + req.dataCount + " 条数据，校验通过");
    }

    @Data
    public static class MockRequest {
        private String periodName;
        private Integer dataCount;
        private String summary;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class MockResponse {
        private boolean ok;
        private String receiptNo;
        private String message;
        private String errorCode;
        private String receivedAt;

        public static MockResponse success(String receipt, String msg) {
            return new MockResponse(true, receipt, msg, null, LocalDateTime.now().format(FMT));
        }
        public static MockResponse failure(String code, String msg) {
            return new MockResponse(false, null, msg, code, LocalDateTime.now().format(FMT));
        }
    }
}
