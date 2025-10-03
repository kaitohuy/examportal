package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.TopicDTO;
import com.exam.examserver.dto.exam.TopicUpsertDTO;
import com.exam.examserver.mapper.TopicMapper;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.exam.Topic;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.TopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/subject/{subjectId}/topics")
@CrossOrigin("*")
public class TopicController {

    private final TopicRepository topicRepo;
    private final SubjectRepository subjectRepo;
    private final TopicMapper mapper;

    public TopicController(TopicRepository topicRepo, SubjectRepository subjectRepo, TopicMapper mapper) {
        this.topicRepo = topicRepo; this.subjectRepo = subjectRepo; this.mapper = mapper;
    }

    @GetMapping
    public List<TopicDTO> list(@PathVariable Long subjectId) {
        Subject subj = subjectRepo.findById(subjectId).orElseThrow();
        return topicRepo.findAll().stream()
                .filter(t -> t.getSubject().getId().equals(subj.getId()))
                .map(mapper::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<TopicDTO> create(@PathVariable Long subjectId, @RequestBody TopicUpsertDTO dto) {
        Subject subj = subjectRepo.findById(subjectId).orElseThrow();
        Topic t = mapper.toEntity(dto);
        t.setSubject(subj);
        if (dto.getParentTopicId() != null)
            t.setParentTopic(topicRepo.findById(dto.getParentTopicId()).orElse(null));
        Topic saved = topicRepo.save(t);
        return ResponseEntity.created(URI.create("/subject/" + subjectId + "/topics/" + saved.getId()))
                .body(mapper.toDto(saved));
    }

    @PutMapping("/{topicId}")
    public TopicDTO update(@PathVariable Long subjectId, @PathVariable Long topicId, @RequestBody TopicUpsertDTO dto) {
        Topic t = topicRepo.findById(topicId).orElseThrow();
        t.setCode(dto.getCode()); t.setName(dto.getName()); t.setOrderIndex(dto.getOrderIndex());
        t.setParentTopic(dto.getParentTopicId() == null ? null : topicRepo.findById(dto.getParentTopicId()).orElse(null));
        return mapper.toDto(topicRepo.save(t));
    }

    @DeleteMapping("/{topicId}")
    public void delete(@PathVariable Long subjectId, @PathVariable Long topicId) {
        topicRepo.deleteById(topicId);
    }
}
