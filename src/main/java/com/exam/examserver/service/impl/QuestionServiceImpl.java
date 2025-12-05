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
    private final QuestionMetaRepository metaRepo;
    private final QuestionFingerprintRepository fpRepo;

    @PersistenceContext
    private EntityManager em;

    public QuestionServiceImpl(QuestionRepository questionRepo,
                               SubjectRepository subjectRepo,
                               UserRepository userRepo,
                               QuestionMapper mapper,
                               ImageStorageService imageStorageService,
                               FingerprintService fingerprintService,
                               QuestionImageRepository imageRepo,
                               BundleItemRepository bundleItemRepo,
                               QuestionBundleRepository bundleRepo,
                               QuestionIssueRepository issueRepo,
                               QuestionMetaRepository metaRepo,
                               QuestionFingerprintRepository fpRepo) {
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
        this.metaRepo = metaRepo;
        this.fpRepo = fpRepo;
    }

    /** Map Question -> DTO + gắn thêm bundleId / bundleInstructions nếu có */
    private QuestionDTO mapToDtoWithBundle(Question q) {
        if (q == null) return null;

        QuestionDTO dto = mapper.toDto(q);

        // Lấy bundle đầu tiên mà câu hỏi thuộc về (nếu có)
        var rows = bundleRepo.findBundleMetaByQuestionId(q.getId());
        if (rows != null && !rows.isEmpty()) {
            Object[] first = rows.get(0); // [0]=bundleId, [1]=instructions
            dto.setBundleId((Long) first[0]);
            dto.setBundleInstructions((String) first[1]);
        } else {
            dto.setBundleId(null);
            dto.setBundleInstructions(null);
        }

        return dto;
    }

    @Override
    public List<QuestionDTO> findByIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) return Collections.emptyList();
        List<Question> entities = questionRepo.findByIdIn(questionIds); // đã lọc non-deleted ở repo
        Map<Long, Question> map = entities.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        return questionIds.stream().map(map::get)
                .filter(Objects::nonNull)
                .map(this::mapToDtoWithBundle)
                .collect(Collectors.toList());
    }

    @Override
    public QuestionDTO getById(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        // NEW: chặn truy cập bản đã xoá mềm
        try {
            var isDeletedField = q.getClass().getDeclaredField("isDeleted"); // phòng trường hợp bạn chưa push model
            isDeletedField.setAccessible(true);
            boolean deleted = (boolean) isDeletedField.get(q);
            if (deleted) throw new EntityNotFoundException("Question not found");
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        return mapToDtoWithBundle(q);
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
        return mapToDtoWithBundle(persisted);
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
        return mapToDtoWithBundle(updated);
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

    @Override
    public void delete(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));

        // set cờ thùng rác
        try {
            var fDeleted = q.getClass().getDeclaredField("isDeleted");
            fDeleted.setAccessible(true);
            fDeleted.set(q, true);
            // nếu có deletedAt
            try {
                var fDeletedAt = q.getClass().getDeclaredField("deletedAt");
                fDeletedAt.setAccessible(true);
                fDeletedAt.set(q, LocalDateTime.now());
            } catch (NoSuchFieldException ignore) {}
            questionRepo.save(q);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // fallback: nếu model chưa có field, tạm hard-delete cũ (không khuyến nghị)
            questionRepo.delete(q);
            return;
        }

        // Rebuild FP cho các bundle liên quan (để FP phản ánh việc câu này tạm thời không còn)
        List<Long> affectedBundleIds = bundleItemRepo.findBundleIdsByQuestionId(questionId);
        if (affectedBundleIds != null && !affectedBundleIds.isEmpty()) {
            Long subjectId = q.getSubject().getId();
            for (Long bid : affectedBundleIds) {
                String stem = bundleRepo.findInstructionsById(bid);
                List<Long> rawQids = bundleRepo.findActiveQuestionIdsInBundle(bid);
                // chỉ lấy các câu chưa bị xoá mềm
                List<Question> activeQs = questionRepo.findByIdIn(rawQids);
                List<String> parts = activeQs.stream().map(Question::getContent).toList();
                fingerprintService.rebuildBundleFP(bid, subjectId, stem, parts);
            }
        }
    }

    @Override
    public void restore(Long questionId) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        try {
            var fDeleted = q.getClass().getDeclaredField("isDeleted");
            fDeleted.setAccessible(true);
            boolean deleted = (boolean) fDeleted.get(q);
            if (!deleted) return;
            fDeleted.set(q, false);
            try {
                var fDeletedAt = q.getClass().getDeclaredField("deletedAt");
                fDeletedAt.setAccessible(true);
                fDeletedAt.set(q, null);
            } catch (NoSuchFieldException ignore) {}
            questionRepo.save(q);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Model Question chưa có field isDeleted/deletedAt");
        }

        // Rebuild FP cho bundle liên quan để đưa câu trở lại footprint
        List<Long> affectedBundleIds = bundleItemRepo.findBundleIdsByQuestionId(questionId);
        if (affectedBundleIds != null && !affectedBundleIds.isEmpty()) {
            Long subjectId = q.getSubject().getId();
            for (Long bid : affectedBundleIds) {
                String stem = bundleRepo.findInstructionsById(bid);
                List<Long> rawQids = bundleRepo.findActiveQuestionIdsInBundle(bid);
                List<Question> activeQs = questionRepo.findByIdIn(rawQids);
                List<String> parts = activeQs.stream().map(Question::getContent).toList();
                fingerprintService.rebuildBundleFP(bid, subjectId, stem, parts);
            }
        }
    }

    @Override
    @Transactional
    public void purge(Long questionId) {
        // LẤY trước các bundle bị ảnh hưởng (active) để lát nữa rebuild FP
        List<Long> affectedBundleIds = bundleItemRepo.findBundleIdsByQuestionId(questionId);

        // Xóa file ảnh vật lý (nên làm best-effort)
        questionRepo.findById(questionId).ifPresent(q -> {
            Set<String> urls = new LinkedHashSet<>();
            if (q.getImageUrl() != null && !q.getImageUrl().isBlank()) urls.add(q.getImageUrl());
            if (q.getImages() != null) {
                for (QuestionImage img : q.getImages())
                    if (img.getUrl() != null && !img.getUrl().isBlank()) urls.add(img.getUrl());
            }
            for (String u : urls) try { imageStorageService.deleteImage(u); } catch (Exception ignored) {}
        });

        // HARD DELETE theo đúng thứ tự
        hardDeleteQuestionsCascade(List.of(questionId));

        // Rebuild FP cho các bundle còn lại
        if (affectedBundleIds != null && !affectedBundleIds.isEmpty()) {
            for (Long bid : affectedBundleIds) {
                String stem = bundleRepo.findInstructionsById(bid);
                // Lấy subjectId của bundle 1 lần, dùng cho mọi trường hợp
                Long subjectId = bundleRepo.findSubjectIdById(bid);

                List<Long> qidsActive = bundleRepo.findActiveQuestionIdsInBundle(bid);
                if (qidsActive.isEmpty()) {
                    // bundle rỗng -> vẫn truyền subjectId của bundle
                    fingerprintService.rebuildBundleFP(bid, subjectId, stem, List.of());
                    continue;
                }

                // bundle còn câu -> rebuild theo danh sách câu hiện hành
                List<Question> qs = questionRepo.findByIdIn(qidsActive);
                List<String> parts = qs.stream().map(Question::getContent).toList();
                fingerprintService.rebuildBundleFP(bid, subjectId, stem, parts);
            }
        }
    }

    @Transactional
    public int purgeAll(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;

        // 1. Thu thập bundle ảnh hưởng
        Set<Long> affectedBundles = new HashSet<>();
        for (Long id : ids) {
            List<Long> list = bundleItemRepo.findBundleIdsByQuestionId(id);
            if (list != null && !list.isEmpty()) affectedBundles.addAll(list);
        }

        // 2. Hard delete questions & dependencies (bao gồm BundleItem)
        int purged = hardDeleteQuestionsCascade(ids);

        // 3. Xử lý các bundle bị ảnh hưởng
        Set<Long> emptyBundles = new HashSet<>();

        for (Long bid : affectedBundles) {
            // Đếm số item còn lại trong bundle
            Long itemCount = bundleItemRepo.countByBundleId(bid);

            if (itemCount == 0) {
                // Bundle rỗng → đánh dấu để xóa
                emptyBundles.add(bid);
            } else {
                // Bundle còn câu → rebuild FP
                String stem = bundleRepo.findInstructionsById(bid);
                Long subjectId = bundleRepo.findSubjectIdById(bid);
                List<Long> qidsActive = bundleRepo.findActiveQuestionIdsInBundle(bid);

                if (subjectId != null && !qidsActive.isEmpty()) {
                    List<Question> qs = questionRepo.findByIdIn(qidsActive);
                    List<String> parts = qs.stream().map(Question::getContent).toList();
                    fingerprintService.rebuildBundleFP(bid, subjectId, stem, parts);
                }
            }
        }

        // 4. Xóa bundle rỗng + FP của chúng
        if (!emptyBundles.isEmpty()) {
            System.out.println("[purgeAll] Deleting " + emptyBundles.size() + " empty bundles: " + emptyBundles);

            // Xóa FP trước (nếu không có CASCADE)
            try {
                em.createQuery("DELETE FROM BundleFingerprint bf WHERE bf.bundleId IN :ids")
                        .setParameter("ids", emptyBundles)
                        .executeUpdate();
            } catch (Exception e) {
                System.err.println("[WARN] Failed to delete bundle fingerprints: " + e.getMessage());
            }

            // Xóa bundle
            bundleRepo.deleteAllById(emptyBundles);
        }

        System.out.println("[purgeAll] purged=" + purged + ", deleted_bundles=" + emptyBundles.size());
        return purged;
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
            int idx = start + i;
            clone.setCloneIndex(idx);

            // === GÁN QUESTION CODE THEO MẪU C<idx>.<base> ===
            String base = Optional.ofNullable(source.getQuestionCode()).orElse("").trim();
            if (!base.isEmpty()) {
                String seed = "C" + idx + "." + base;   // vd: C3.NH1.11.b)
                String candidate = seed;

                // Phòng va chạm trong cùng subject (do có thể có một câu khác trùng hệt)
                // -> chỉ khi va chạm mới gắn hậu tố -1, -2...
                int bump = 0;
                while (questionRepo.existsBySubjectIdAndQuestionCodeIgnoreCaseAndIsDeletedFalse(subjectId, candidate)) {
                    bump++;
                    candidate = seed + "-" + bump;      // C3.NH1.11.b)-1, ...
                }
                clone.setQuestionCode(candidate);
            }

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
            out.add(mapToDtoWithBundle(saved));
        }

        return out;
    }

    @Override
    public Page<QuestionDTO> getClones(Long questionId, Pageable pageable) {
        Question parent = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Long rootId = (parent.getParent() == null ? parent.getId() : parent.getParent().getId());
        Page<Question> page = questionRepo.findClonesByParentId(rootId, pageable);
        return page.map(this::mapToDtoWithBundle);
    }

    @Override
    public Page<QuestionDTO> pageBySubject(Long subjectId, QuestionFilter f, Pageable pageable) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        Specification<Question> spec = Specification.allOf(
                QuestionSpecs.subjectId(subjectId),
                QuestionSpecs.isRootOnly(),
                QuestionSpecs.notDeleted(),                      // NEW: lọc thùng rác
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
        Page<QuestionDTO> pageDto = page.map(this::mapToDtoWithBundle);

        // gắn cờ flagged theo batch (giữ nguyên)
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
                (f.getDeletedOnly() != null && f.getDeletedOnly())
                        ? QuestionSpecs.deletedOnly()
                        : QuestionSpecs.notDeleted(),
                QuestionSpecs.hasAnyLabel(f.getLabels()),
                QuestionSpecs.difficulty(f.getDifficulty()),
                QuestionSpecs.chapter(f.getChapter()),
                QuestionSpecs.type(f.getType()),
                QuestionSpecs.createdByContains(f.getCreatedBy()),
                QuestionSpecs.createdBetween(f.getFrom(), f.getTo()),
                QuestionSpecs.fullText(f.getQ()),
                QuestionSpecs.flagged(f.getFlagged())
        );

        return questionRepo.findAll(spec).stream()
                .map(Question::getId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional
    public int deleteAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int ok = 0;
        for (Long id : ids) { delete(id); ok++; }
        return ok;
    }

    @Override
    public void updateQuestionCode(Long questionId, String code) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        q.setQuestionCode(code);
        questionRepo.save(q);
    }

    @Override
    public boolean codeExists(Long subjectId, String code) {
        return (code != null && !code.isBlank())
                // OLD (comment): cho cả bản đã xoá
                // && questionRepo.existsBySubjectIdAndQuestionCode(subjectId, code);
                // NEW: chỉ xét bản chưa xoá
                && ( questionRepo.existsBySubjectIdAndQuestionCodeAndIsDeletedFalse(subjectId, code)
                || questionRepo.existsBySubjectIdAndQuestionCodeIgnoreCaseAndIsDeletedFalse(subjectId, code) );
    }

    @Override
    public Page<QuestionDTO> pageDeletedBySubject(Long subjectId, QuestionFilter f, Pageable pageable) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        Specification<Question> spec = Specification.allOf(
                QuestionSpecs.subjectId(subjectId),
//                QuestionSpecs.isRootOnly(),
                QuestionSpecs.deletedOnly(),                 // LƯU Ý: khác pageBySubject()
                QuestionSpecs.hasAnyLabel(f.getLabels()),
                QuestionSpecs.difficulty(f.getDifficulty()),
                QuestionSpecs.chapter(f.getChapter()),
                QuestionSpecs.type(f.getType()),
                QuestionSpecs.createdByContains(f.getCreatedBy()),
                QuestionSpecs.createdBetween(f.getFrom(), f.getTo()),
                QuestionSpecs.fullText(f.getQ())
        );

        Page<Question> page = questionRepo.findAll(spec, pageable);
        Page<QuestionDTO> pageDto = page.map(this::mapToDtoWithBundle);

        // (Không cần gắn cờ flagged trong thùng rác — tuỳ bạn, có thể giữ nếu muốn)
        return pageDto;
    }

    private Set<Long> expandWithAllDescendants(Collection<Long> roots) {
        Set<Long> all = new LinkedHashSet<>(roots);
        Deque<Long> dq = new ArrayDeque<>(roots);
        while (!dq.isEmpty()) {
            Long p = dq.pollFirst();
            List<Long> children = questionRepo.findChildIds(List.of(p));
            for (Long c : children) if (all.add(c)) dq.addLast(c);
        }
        return all;
    }

    @Transactional
    protected int hardDeleteQuestionsCascade(Collection<Long> rootIds) {
        if (rootIds == null || rootIds.isEmpty()) return 0;

        // 1) Gom tất cả id (bao gồm clone con nhiều tầng) & xoá con trước cha
        Set<Long> allIds = expandWithAllDescendants(rootIds);

        // 2) DỌN PHỤ THUỘC (hard delete)
        // 2.1 quiz_question (nếu có Repo, bạn có thể gọi ở đây) — bỏ qua nếu không dùng
        // quizQuestionRepo.deleteByQuestionIdIn(allIds);

        // 2.2 bundle_item → BẮT BUỘC xoá để gỡ FK question_id
        bundleItemRepo.hardDeleteByQuestionIds(allIds);

        // 2.3 question_labels (element collection)
        try { questionRepo.deleteLabelsByQuestionIds(allIds); } catch (Exception ignore) {}

        // 2.4 question_image (gallery)
        imageRepo.deleteByQuestionIdIn(allIds);

        // 2.5 question_issue (1-1)
        issueRepo.deleteByQuestionIdIn(allIds);

        // 2.6 question_meta + question_fingerprint (1-1; PK = question_id)
        metaRepo.deleteAllByIdInBatch(allIds);
        fpRepo.deleteAllByIdInBatch(allIds);

        // 3) XÓA CHÍNH QUESTION
        return questionRepo.deleteByIdIn(allIds);
    }

    @Override
    public List<Long> findIdsByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        return questionRepo.findAllByQuestionCodeIn(codes)
                .stream()
                .map(Question::getId)
                .toList();
    }

    @Override
    public Map<String, Long> findIdMapByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return Map.of();

        List<String> lower = codes.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))  // chỉ lowercase + trim
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        var questions = questionRepo.findAllByQuestionCodeLowerIn(lower);

        Map<String, Long> map = new HashMap<>();
        for (var q : questions) {
            String key = Optional.ofNullable(q.getQuestionCode())
                    .orElse("")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) map.putIfAbsent(key, q.getId());
        }
        return map;
    }

    @Override
    public Optional<Long> findParentIdByCode(Long subjectId, String baseCode) {
        if (subjectId == null || baseCode == null || baseCode.isBlank()) return Optional.empty();
        return questionRepo.findBySubjectIdAndQuestionCodeIgnoreCase(subjectId, baseCode.trim())
                .map(Question::getId);
    }

    @Override
    @Transactional
    public void convertToClone(Long questionId, Long parentId, int cloneIndex, String finalCode) {
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found"));
        Question parent = questionRepo.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        if (parent.getParent() != null)
            throw new IllegalArgumentException("Parent must be a root question");

        if (!Objects.equals(q.getSubject().getId(), parent.getSubject().getId()))
            throw new IllegalArgumentException("Subject mismatch between clone and parent");

        // đảm bảo code duy nhất trong môn
        if (questionRepo.existsBySubjectIdAndQuestionCodeIgnoreCase(parent.getSubject().getId(), finalCode))
            throw new IllegalStateException("Question code already exists: " + finalCode);

        q.setParent(parent);
        q.setCloneIndex(cloneIndex);
        q.setQuestionCode(finalCode);
        questionRepo.save(q);
    }

}
