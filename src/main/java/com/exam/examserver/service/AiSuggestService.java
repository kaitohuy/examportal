package com.exam.examserver.service;

import com.exam.examserver.dto.ai.AiSuggestRequest;
import com.exam.examserver.dto.ai.AiSuggestResponse;

/**
 * Interface cho các dịch vụ gợi ý đáp án bằng AI (OpenAI, Azure, v.v.)
 */
public interface AiSuggestService {

    /**
     * Gợi ý đáp án dựa trên nội dung câu hỏi.
     * @param req dữ liệu câu hỏi (nội dung, loại, mức độ...)
     * @return gợi ý đáp án AI trả về
     */
    AiSuggestResponse suggest(AiSuggestRequest req);
}
