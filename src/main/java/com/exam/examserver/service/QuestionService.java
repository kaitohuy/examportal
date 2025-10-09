package com.exam.examserver.service;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.exam.QuestionFilter;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.CloneRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

public interface QuestionService {
    QuestionDTO getById(Long questionId);
    QuestionDTO create(Long subjectId, CreateQuestionDTO payload, Long creatorUserId,
                       List<MultipartFile> images);
    QuestionDTO update(Long questionId, CreateQuestionDTO payload,
                       List<MultipartFile> images);
    void delete(Long questionId);

    List<QuestionDTO> findByIds(List<Long> questionIds);
    void addImages(Long questionId, List<String> imageUrls); // (tuỳ)

    List<QuestionDTO> cloneQuestion(Long subjectId, Long questionId, Long creatorUserId, CloneRequest req);
    Page<QuestionDTO> getClones(Long questionId, Pageable pageable);
    Page<QuestionDTO> pageBySubject(Long subjectId, QuestionFilter filter, Pageable pageable);
    List<Long> findIdsByFilter(Long subjectId, QuestionFilter filter);
    int deleteAllByIds(List<Long> ids);
    void updateQuestionCode(Long questionId, String code);
    boolean codeExists(Long subjectId, String code);
}
