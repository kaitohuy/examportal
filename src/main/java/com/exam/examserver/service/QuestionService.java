package com.exam.examserver.service;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.exam.QuestionFilter;
import com.exam.examserver.model.exam.CloneRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionService {
    QuestionDTO getById(Long questionId);

    QuestionDTO create(Long subjectId, CreateQuestionDTO payload, Long creatorUserId, List<MultipartFile> images);
    QuestionDTO update(Long questionId, CreateQuestionDTO payload, List<MultipartFile> images);

    /** Soft delete (đưa vào thùng rác) */
    void delete(Long questionId);

    /** Khôi phục từ thùng rác */
    void restore(Long questionId);

    /** Xoá cứng */
    void purge(Long questionId);

    List<QuestionDTO> findByIds(List<Long> questionIds);
    void addImages(Long questionId, List<String> imageUrls);

    List<QuestionDTO> cloneQuestion(Long subjectId, Long questionId, Long creatorUserId, CloneRequest req);
    Page<QuestionDTO> getClones(Long questionId, Pageable pageable);

    /** Danh sách đang dùng (không bao gồm trash) */
    Page<QuestionDTO> pageBySubject(Long subjectId, QuestionFilter filter, Pageable pageable);

    /** Danh sách trong thùng rác */
    Page<QuestionDTO> pageDeletedBySubject(Long subjectId, QuestionFilter filter, Pageable pageable);

    List<Long> findIdsByFilter(Long subjectId, QuestionFilter filter);

    /** Soft delete hàng loạt */
    int deleteAllByIds(List<Long> ids);

    void updateQuestionCode(Long questionId, String code);
    boolean codeExists(Long subjectId, String code);
}
