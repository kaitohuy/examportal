package com.exam.examserver.service.impl;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.exam.QuestionFilter;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.IssueStatus;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.mapper.QuestionMapper;
import com.exam.examserver.model.exam.*;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.*;
import com.exam.examserver.repo.spec.QuestionSpecs;
import com.exam.examserver.service.dup.FingerprintService;
import com.exam.examserver.storage.ImageStorageService;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.util.TextSim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final FingerprintService fingerprintService;
    private final QuestionImageRepository imageRepo;
    private final BundleItemRepository bundleItemRepo;
    private final QuestionBundleRepository bundleRepo;
    private final QuestionIssueRepository issueRepo;

    @PersistenceContext
    private EntityManager em;

    public QuestionServiceImpl(QuestionRepository questionRepo,
                               SubjectRepository subjectRepo,
                               UserRepository userRepo,
                               QuestionMapper mapper,
                               ImageStorageService imageStorageService, FingerprintService fingerprintService, QuestionImageRepository imageRepo, BundleItemRepository bundleItemRepo, QuestionBundleRepository bundleRepo, QuestionIssueRepository issueRepo) {
        this.questionRepo = questionRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.mapper = mapper;
        this.imageStorageService = imageStorageService;
        this.fingerprintService = fingerprintService;
        this.imageRepo = imageRepo;
        this.bundleItemRepo = bundleItemRepo;
        this.bundleRepo = bundleRepo;
        this.issueRepo = issueRepo;
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
    public QuestionDTO create(Long subjectId, CreateQuestionDTO payload, Long creatorUserId, List<MultipartFile> images) {
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

        String probe = (payload.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
                ? TextSim.packMultipleChoice(payload.getContent(),
                payload.getOptionA(), payload.getOptionB(), payload.getOptionC(), payload.getOptionD())
                : payload.getContent();

        Question saved = questionRepo.save(q);

        // Lưu gallery nếu có
        if (images != null && !images.isEmpty()) {
            List<String> urls = storeImages(saved.getId(), images);
            applyGallery(saved, urls, /*replace*/ true);
        }
        Question persisted = questionRepo.save(saved);
        fingerprintService.upsert(persisted);
        return mapper.toDto(persisted);
    }

    @Override
    public QuestionDTO update(Long questionId, CreateQuestionDTO payload, List<MultipartFile> images) {
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

        if (images != null) {
            // xóa file vật lý ảnh cũ
            Set<String> oldUrls = new LinkedHashSet<>();
            if (q.getImages() != null) {
                for (QuestionImage im : q.getImages()) if (im.getUrl() != null) oldUrls.add(im.getUrl());
            }
            if (q.getImageUrl() != null) oldUrls.add(q.getImageUrl()); // cover cũ (sẽ set lại sau)
            for (String u : oldUrls) {
                try { imageStorageService.deleteImage(u); } catch (Exception ignored) {}
            }

            // xóa record gallery cũ
            imageRepo.deleteByQuestionId(q.getId());
            q.getImages().clear();
            q.setImageUrl(null);

            // lưu gallery mới
            if (!images.isEmpty()) {
                List<String> urls = storeImages(q.getId(), images);
                applyGallery(q, urls, /*replace*/ true);
            }
        }

        String probe = (payload.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
                ? TextSim.packMultipleChoice(payload.getContent(),
                payload.getOptionA(), payload.getOptionB(), payload.getOptionC(), payload.getOptionD())
                : payload.getContent();

        Question updated = questionRepo.save(q);
        fingerprintService.upsert(updated);
        return mapper.toDto(updated);
    }

    private List<String> storeImages(Long questionId, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            try {
                urls.add(imageStorageService.storeImage(f, questionId));
            } catch (IOException e) {
                throw new RuntimeException("Upload ảnh thất bại", e);
            }
        }
        return urls;
    }

    /** Áp dụng gallery & cover theo danh sách URL. Nếu replace=true, luôn set lại từ đầu. */
    private void applyGallery(Question q, List<String> urls, boolean replace) {
        int start = replace ? 0 : (q.getImages() == null ? 0 : q.getImages().size());
        int idx = 0;
        for (String url : urls) {
            QuestionImage gi = new QuestionImage();
            gi.setQuestion(q);
            gi.setUrl(url);
            gi.setOrderIndex(start + (++idx)); // 1-based
            q.getImages().add(gi);
        }
        if (!urls.isEmpty()) q.setImageUrl(urls.get(0)); // cover = ảnh đầu
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

//    @Override
//    public void delete(Long questionId) {
//        Question q = questionRepo.findById(questionId)
//                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
//
//        // Xoá ảnh cover + gallery (không fail toàn bộ nếu 1 ảnh lỗi)
//        Set<String> urls = new LinkedHashSet<>();
//        if (q.getImageUrl() != null && !q.getImageUrl().isEmpty()) urls.add(q.getImageUrl());
//        if (q.getImages() != null) {
//            for (QuestionImage img : q.getImages()) {
//                if (img.getUrl() != null && !img.getUrl().isEmpty()) urls.add(img.getUrl());
//            }
//        }
//        for (String url : urls) {
//            try { imageStorageService.deleteImage(url); } catch (Exception ignored) {}
//        }
//
//        questionRepo.delete(q);
//    }

    @Override
    public void delete(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        List<Long> affectedBundleIds = bundleItemRepo.findBundleIdsByQuestionId(questionId);

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

        if (affectedBundleIds != null && !affectedBundleIds.isEmpty()) {
            for (Long bid : affectedBundleIds) {
                if (bundleItemRepo.countByBundleId(bid) == 0) {
                    bundleRepo.deleteById(bid);
                }
            }
        }
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

//    @Override
//    public List<QuestionDTO> getClones(Long questionId) {
//        Question parent = questionRepo.findById(questionId)
//                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
//        Long rootId = (parent.getParent() == null ? parent.getId() : parent.getParent().getId());
//        return questionRepo.findClonesByParentId(rootId)
//                .stream().map(mapper::toDto).collect(Collectors.toList());
//    }

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

    @Override
    public Page<QuestionDTO> getClones(Long questionId, Pageable pageable) {
        Question parent = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Long rootId = (parent.getParent() == null ? parent.getId() : parent.getParent().getId());
        Page<Question> page = questionRepo.findClonesByParentId(rootId, pageable);
        return page.map(mapper::toDto);
    }

    @Override
    public Page<QuestionDTO> pageBySubject(Long subjectId, QuestionFilter f, Pageable pageable) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        Specification<Question> spec = Specification.allOf(
                QuestionSpecs.subjectId(subjectId),
                QuestionSpecs.isRootOnly(),
                QuestionSpecs.hasAnyLabel(f.getLabels()),
                QuestionSpecs.difficulty(f.getDifficulty()),
                QuestionSpecs.chapter(f.getChapter()),
                QuestionSpecs.type(f.getType()),
                QuestionSpecs.createdByContains(f.getCreatedBy()),
                QuestionSpecs.createdBetween(f.getFrom(), f.getTo()),
                QuestionSpecs.fullText(f.getQ()),
                QuestionSpecs.flagged(f.getFlagged())
        );

        Page<Question> page = questionRepo.findAll(spec, pageable);
        Page<QuestionDTO> pageDto = page.map(mapper::toDto);

// gắn cờ flagged theo batch
        List<Long> ids = pageDto.getContent().stream().map(QuestionDTO::getId).toList();
        if (!ids.isEmpty()) {
            var openIssues = issueRepo.findByQuestionIdInAndStatus(ids, IssueStatus.OPEN);
            var flaggedSet = openIssues.stream().map(ii -> ii.getQuestion().getId()).collect(Collectors.toSet());
            pageDto.getContent().forEach(dto -> dto.setFlagged(flaggedSet.contains(dto.getId())));
        }

        return pageDto;

    }

    @Override
    public List<Long> findIdsByFilter(Long subjectId, QuestionFilter f) {
        Specification<Question> spec = Specification.allOf(
                QuestionSpecs.subjectId(subjectId),
                QuestionSpecs.isRootOnly(),
                QuestionSpecs.hasAnyLabel(f.getLabels()),
                QuestionSpecs.difficulty(f.getDifficulty()),
                QuestionSpecs.chapter(f.getChapter()),
                QuestionSpecs.type(f.getType()),
                QuestionSpecs.createdByContains(f.getCreatedBy()),
                QuestionSpecs.createdBetween(f.getFrom(), f.getTo()),
                QuestionSpecs.fullText(f.getQ()),
                QuestionSpecs.flagged(f.getFlagged())
        );

        // KHÔNG truyền sort/pageable để tránh ORDER BY trên DISTINCT (Postgres 42P10)
        return questionRepo.findAll(spec).stream()
                .map(Question::getId)
                .distinct() // phòng trường hợp join labels sinh duplicate
                .toList();
    }

//    @Override
//    @Transactional
//    public int deleteAllByIds(List<Long> ids) {
//        if (ids == null || ids.isEmpty()) return 0;
//        questionRepo.deleteAllByIdInBatch(ids);
//        return ids.size();
//    }

    @Override
    @Transactional
    public int deleteAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int ok = 0;
        for (Long id : ids) {
            delete(id); // dùng hàm delete đã viết ở trên
            ok++;
        }
        return ok;
    }
}
