package com.exam.examserver.service;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.CloneRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

public interface QuestionService {
    List<QuestionDTO> getAllBySubject(Long subjectId);

    // Lọc theo nhãn (PRACTICE/EXAM), chỉ trả bản gốc (parent IS NULL)
    List<QuestionDTO> getAllBySubject(Long subjectId, Set<QuestionLabel> labelsFilter);

    QuestionDTO getById(Long questionId);

    QuestionDTO create(Long subjectId, CreateQuestionDTO payload, Long creatorUserId, MultipartFile image);

    QuestionDTO update(Long questionId, CreateQuestionDTO payload, MultipartFile image);

    void delete(Long questionId);

    List<QuestionDTO> findByIds(List<Long> questionIds);

    void updateImageUrl(Long questionId, String imageUrl);

    void addImages(Long questionId, List<String> imageUrls);

    // ===== Clone API =====
    List<QuestionDTO> getClones(Long questionId);

    List<QuestionDTO> cloneQuestion(Long subjectId, Long questionId, Long creatorUserId, CloneRequest req);
}
