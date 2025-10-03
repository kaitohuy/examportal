package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.QuestionMetaDTO;
import com.exam.examserver.dto.exam.QuestionMetaUpsertDTO;
import com.exam.examserver.enums.*;
import com.exam.examserver.mapper.QuestionMetaMapper;
import com.exam.examserver.model.exam.QuestionMeta;
import com.exam.examserver.repo.QuestionMetaRepository;
import com.exam.examserver.repo.spec.QuestionMetaSpecs;
import com.exam.examserver.service.QuestionMetaService;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin("*")
public class QuestionMetaController {

    private final QuestionMetaRepository metaRepo;
    private final QuestionMetaService metaService;
    private final QuestionMetaMapper mapper;

    public QuestionMetaController(QuestionMetaRepository metaRepo, QuestionMetaService metaService, QuestionMetaMapper mapper) {
        this.metaRepo = metaRepo; this.metaService = metaService; this.mapper = mapper;
    }

    // Upsert meta cho 1 question
    @PutMapping("/questions/{questionId}/meta")
    public QuestionMetaDTO upsert(@PathVariable Long questionId, @RequestBody QuestionMetaUpsertDTO dto) {
        QuestionMeta saved = metaService.upsertDefault(
                questionId,
                dto.getUnitKind() == null ? UnitKind.FULL_QUESTION : dto.getUnitKind(),
                dto.getPoints() == null ? new BigDecimal("1.00") : dto.getPoints(),
                dto.getChapter(),
                dto.getTopicId(),
                dto.getStatus() == null ? RecordStatus.DRAFT : dto.getStatus(),
                dto.getCognitiveLevel(),
                dto.getTypeCode()
        );
        return mapper.toDto(saved);
    }

    @GetMapping("/questions/{questionId}/meta")
    public QuestionMetaDTO get(@PathVariable Long questionId) {
        QuestionMeta m = metaRepo.findById(questionId).orElseThrow();
        return mapper.toDto(m);
    }

    // Tìm meta theo subject + filter động (Specification)
    @GetMapping("/subject/{subjectId}/question-meta")
    public Page<QuestionMetaDTO> search(
            @PathVariable Long subjectId,
            @RequestParam(required = false) UnitKind unitKind,
            @RequestParam(required = false) CognitiveLevel cognitive,
            @RequestParam(required = false) List<Integer> chapterIn,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) BigDecimal pointsMin,
            @RequestParam(required = false) BigDecimal pointsMax,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) String q, // full-text trên Question.content
            @RequestParam(required = false) QuestionType questionType, // MCQ/ESSAY
            @PageableDefault(size = 20, sort = "questionId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Specification<QuestionMeta> spec = Specification.allOf(
                QuestionMetaSpecs.bySubjectId(subjectId),
                QuestionMetaSpecs.unitKind(unitKind),
                QuestionMetaSpecs.cognitive(cognitive),
                QuestionMetaSpecs.chapterIn(chapterIn),
                QuestionMetaSpecs.topicId(topicId),
                QuestionMetaSpecs.typeCodeIn(typeCode == null ? null : List.of(typeCode)),
                QuestionMetaSpecs.pointsBetween(pointsMin, pointsMax),
                QuestionMetaSpecs.status(status),
                QuestionMetaSpecs.fullTextContains(q),
                QuestionMetaSpecs.questionType(questionType)
        );
        return metaRepo.findAll(spec, pageable).map(mapper::toDto);
    }

    // Đánh dấu sử dụng (gọi sau khi sinh đề)
    @PostMapping("/meta/mark-used")
    public void markUsed(@RequestBody List<Long> questionIds) {
        metaService.markUsed(questionIds);
    }
}
