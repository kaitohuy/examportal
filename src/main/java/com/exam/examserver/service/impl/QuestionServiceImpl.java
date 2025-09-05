package com.exam.examserver.service.impl;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.mapper.QuestionMapper;
import com.exam.examserver.model.exam.*;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.storage.ImageStorageService;
import com.exam.examserver.dto.importing.ImportPreviewStore;
import com.exam.examserver.service.QuestionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final QuestionMapper mapper;
    private final ImageStorageService imageStorageService;

    public QuestionServiceImpl(QuestionRepository questionRepo,
                               SubjectRepository subjectRepo,
                               UserRepository userRepo,
                               QuestionMapper mapper,
                               ImageStorageService imageStorageService) {
        this.questionRepo = questionRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.mapper = mapper;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public List<QuestionDTO> getAllBySubject(Long subjectId) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        return questionRepo.findBySubjectId(subjectId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<QuestionDTO> getAllBySubject(Long subjectId, Set<QuestionLabel> labelsFilter) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        List<Question> list = (labelsFilter != null && !labelsFilter.isEmpty())
                ? questionRepo.findBySubjectIdAndAnyLabelIn(subjectId, labelsFilter)
                : questionRepo.findBySubjectId(subjectId);

        return list.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<QuestionDTO> findByIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return Collections.emptyList();
        List<Question> entities = questionRepo.findByIdIn(questionIds);
        Map<Long, Question> map = entities.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        return questionIds.stream().map(map::get)
                .filter(Objects::nonNull)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public QuestionDTO getById(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        return mapper.toDto(q);
    }

    @Override
    public QuestionDTO create(Long subjectId, CreateQuestionDTO payload, Long creatorUserId, MultipartFile image) {
        Subject subject = subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        User creator = userRepo.findById(creatorUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        validateQuestionPayload(payload);

        Question q = mapper.toEntity(payload);
        q.setSubject(subject);
        q.setCreatedBy(creator);
        q.setCreatedAt(LocalDateTime.now());

        // labels mặc định PRACTICE nếu null/empty
        Set<QuestionLabel> labels = normalizeLabels(payload.getLabels());
        q.setLabels(labels);

        // (Tuỳ chọn) nếu muốn tạo clone qua create: parentId != null
        if (payload.getParentId() != null) {
            Question root = questionRepo.findById(payload.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent question not found"));
            if (root.getParent() != null) {
                throw new IllegalArgumentException("Cannot create clone from a clone");
            }
            if (!Objects.equals(root.getSubject().getId(), subjectId)) {
                throw new IllegalArgumentException("Subject mismatch");
            }

            // nếu labels trống → copy nhãn của root
            if (labels == null || labels.isEmpty()) {
                q.setLabels(EnumSet.copyOf(root.getLabels()));
            }
            q.setParent(root);
            Integer maxIdx = questionRepo.findMaxCloneIndexByParentId(root.getId());
            q.setCloneIndex((maxIdx == null ? 0 : maxIdx) + 1);
        }

        Question saved = questionRepo.save(q);

        if (image != null && !image.isEmpty()) {
            try {
                String newImageUrl = imageStorageService.storeImage(image, saved.getId());
                saved.setImageUrl(newImageUrl);
                questionRepo.save(saved);
            } catch (IOException e) {
                throw new RuntimeException("Lỗi khi upload hình ảnh", e);
            }
        }

        return mapper.toDto(saved);
    }

    @Override
    public QuestionDTO update(Long questionId, CreateQuestionDTO payload, MultipartFile image) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        // Không cho sửa parent/cloneIndex qua update
        validateQuestionPayload(payload);

        q.setQuestionType(payload.getQuestionType());
        q.setContent(payload.getContent());
        q.setDifficulty(payload.getDifficulty());
        q.setChapter(payload.getChapter());
        q.setOptionA(payload.getOptionA());
        q.setOptionB(payload.getOptionB());
        q.setOptionC(payload.getOptionC());
        q.setOptionD(payload.getOptionD());
        q.setAnswer(payload.getAnswer());
        q.setAnswerText(payload.getAnswerText());

        q.setLabels(normalizeLabels(payload.getLabels()));

        if (image != null && !image.isEmpty()) {
            try {
                if (q.getImageUrl() != null) {
                    imageStorageService.deleteImage(q.getImageUrl());
                }
                String imageUrl = imageStorageService.storeImage(image, q.getId());
                q.setImageUrl(imageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload image", e);
            }
        }

        Question updated = questionRepo.save(q);
        return mapper.toDto(updated);
    }

    private Set<QuestionLabel> normalizeLabels(Set<QuestionLabel> in) {
        return (in == null || in.isEmpty()) ? EnumSet.of(QuestionLabel.PRACTICE) : EnumSet.copyOf(in);
    }

    private void validateQuestionPayload(CreateQuestionDTO payload) {
        if (payload.getQuestionType() == null)
            throw new IllegalArgumentException("Question type is required");

        switch (payload.getQuestionType()) {
            case MULTIPLE_CHOICE:
                if (payload.getOptionA() == null || payload.getOptionB() == null ||
                        payload.getOptionC() == null || payload.getOptionD() == null ||
                        payload.getAnswer() == null) {
                    throw new IllegalArgumentException("Multiple-choice requires options A–D and an answer");
                }
                if (!Arrays.asList("A", "B", "C", "D").contains(payload.getAnswer())) {
                    throw new IllegalArgumentException("Answer must be A, B, C, or D");
                }
                break;

            case ESSAY:
                if (payload.getAnswerText() != null && payload.getAnswerText().isBlank()) {
                    payload.setAnswerText(null);
                }
                payload.setOptionA(null);
                payload.setOptionB(null);
                payload.setOptionC(null);
                payload.setOptionD(null);
                payload.setAnswer(null);
                break;

            default:
                throw new IllegalArgumentException("Unsupported question type");
        }
    }

    @Override
    public void delete(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        // Xoá ảnh cover + gallery (không fail toàn bộ nếu 1 ảnh lỗi)
        Set<String> urls = new LinkedHashSet<>();
        if (q.getImageUrl() != null && !q.getImageUrl().isEmpty()) urls.add(q.getImageUrl());
        if (q.getImages() != null) {
            for (QuestionImage img : q.getImages()) {
                if (img.getUrl() != null && !img.getUrl().isEmpty()) urls.add(img.getUrl());
            }
        }
        for (String url : urls) {
            try { imageStorageService.deleteImage(url); } catch (Exception ignored) {}
        }

        questionRepo.delete(q);
    }

    @Override
    public void updateImageUrl(Long questionId, String imageUrl) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));
        q.setImageUrl(imageUrl);
        questionRepo.save(q);
    }

    @Override
    public void addImages(Long questionId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found: " + questionId));

        int startIndex = q.getImages().size() + 1;
        for (int i = 0; i < imageUrls.size(); i++) {
            QuestionImage img = new QuestionImage();
            img.setQuestion(q);
            img.setUrl(imageUrls.get(i));
            img.setOrderIndex(startIndex + i);
            q.getImages().add(img);
        }
        if (q.getImageUrl() == null) q.setImageUrl(imageUrls.get(0));
        questionRepo.save(q);
    }

    // ===== Clone APIs =====

    @Override
    public List<QuestionDTO> getClones(Long questionId) {
        Question parent = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Long rootId = (parent.getParent() == null ? parent.getId() : parent.getParent().getId());
        return questionRepo.findClonesByParentId(rootId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<QuestionDTO> cloneQuestion(Long subjectId, Long questionId, Long creatorUserId, CloneRequest req) {
        if (req == null) req = new CloneRequest();
        int count = Math.max(1, req.getCount());

        Question source = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        // chặn clone-of-clone
        if (source.getParent() != null) {
            throw new IllegalArgumentException("Không thể nhân bản từ một bản sao. Hãy nhân bản từ câu gốc.");
        }
        if (!Objects.equals(source.getSubject().getId(), subjectId)) {
            throw new IllegalArgumentException("Subject mismatch");
        }

        Set<QuestionLabel> labels = (req.getLabels() != null && !req.getLabels().isEmpty())
                ? EnumSet.copyOf(req.getLabels())
                : EnumSet.copyOf(source.getLabels());
        Difficulty diff = (req.getDifficulty() != null) ? req.getDifficulty() : source.getDifficulty();
        Integer chapter = (req.getChapter() != null) ? req.getChapter() : source.getChapter();

        int start = questionRepo.findMaxCloneIndexByParentId(source.getId()) + 1;

        User creator = userRepo.findById(creatorUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<QuestionDTO> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Question clone = new Question();
            clone.setSubject(source.getSubject());
            clone.setQuestionType(source.getQuestionType());
            clone.setDifficulty(diff);
            clone.setChapter(chapter);
            clone.setCreatedBy(creator);
            clone.setCreatedAt(LocalDateTime.now());
            clone.setLabels(EnumSet.copyOf(labels));

            // copy nội dung gốc (người dùng sẽ sửa thủ công ở FE)
            clone.setContent(source.getContent());
            clone.setOptionA(source.getOptionA());
            clone.setOptionB(source.getOptionB());
            clone.setOptionC(source.getOptionC());
            clone.setOptionD(source.getOptionD());
            clone.setAnswer(source.getAnswer());
            clone.setAnswerText(source.getAnswerText());
            clone.setImageUrl(source.getImageUrl());

            // metadata clone
            clone.setParent(source);
            clone.setCloneIndex(start + i);

            // copy gallery (re-use URL) nếu yêu cầu
            if (Boolean.TRUE.equals(req.getCopyImages()) && source.getImages() != null) {
                for (QuestionImage im : source.getImages()) {
                    QuestionImage ni = new QuestionImage();
                    ni.setQuestion(clone);
                    ni.setUrl(im.getUrl());
                    ni.setOrderIndex(im.getOrderIndex());
                    clone.getImages().add(ni);
                }
            }

            // validate theo loại
            CreateQuestionDTO shadow = new CreateQuestionDTO();
            shadow.setQuestionType(clone.getQuestionType());
            shadow.setContent(clone.getContent());
            shadow.setDifficulty(clone.getDifficulty());
            shadow.setChapter(clone.getChapter());
            shadow.setOptionA(clone.getOptionA());
            shadow.setOptionB(clone.getOptionB());
            shadow.setOptionC(clone.getOptionC());
            shadow.setOptionD(clone.getOptionD());
            shadow.setAnswer(clone.getAnswer());
            shadow.setAnswerText(clone.getAnswerText());
            validateQuestionPayload(shadow);

            Question saved = questionRepo.save(clone);
            out.add(mapper.toDto(saved));
        }

        return out;
    }
}
