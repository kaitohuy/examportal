package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.BundleUpsertDTO;
import com.exam.examserver.dto.exam.BundleUpsertItemDTO;
import com.exam.examserver.dto.exam.QuestionBundleDTO;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.mapper.QuestionBundleMapper;
import com.exam.examserver.model.exam.BundleItem;
import com.exam.examserver.model.exam.QuestionBundle;
import com.exam.examserver.repo.BundleItemRepository;
import com.exam.examserver.repo.QuestionBundleRepository;
import com.exam.examserver.repo.spec.QuestionBundleSpecs;
import com.exam.examserver.service.BundleService;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/subject/{subjectId}/bundles")
@CrossOrigin("*")
public class QuestionBundleController {

    private final QuestionBundleRepository bundleRepo;
    private final BundleItemRepository itemRepo;
    private final BundleService bundleService;
    private final QuestionBundleMapper mapper;

    public QuestionBundleController(QuestionBundleRepository bundleRepo,
                                    BundleItemRepository itemRepo,
                                    BundleService bundleService,
                                    QuestionBundleMapper mapper) {
        this.bundleRepo = bundleRepo; this.itemRepo = itemRepo; this.bundleService = bundleService; this.mapper = mapper;
    }

    @GetMapping
    public Page<QuestionBundleDTO> list(@PathVariable Long subjectId,
                                        @RequestParam(required = false) RecordStatus status,
                                        @RequestParam(required = false) String title,
                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Specification<QuestionBundle> spec = Specification.allOf(
                QuestionBundleSpecs.bySubjectId(subjectId),
                QuestionBundleSpecs.status(status),
                QuestionBundleSpecs.titleContains(title)
        );
        return bundleRepo.findAll(spec, pageable).map(mapper::toDto);
    }

    @GetMapping("/{bundleId}")
    public QuestionBundleDTO get(@PathVariable Long subjectId, @PathVariable Long bundleId) {
        QuestionBundle b = bundleRepo.findById(bundleId).orElseThrow();
        return mapper.toDto(b);
    }

    @PostMapping
    public QuestionBundleDTO create(@PathVariable Long subjectId,
                                    @RequestParam Long createdByUserId,
                                    @RequestBody BundleUpsertDTO dto) {
        List<BundleService.CreateItem> items = new ArrayList<>();
        if (dto.getItems() != null) {
            for (BundleUpsertItemDTO it : dto.getItems()) {
                items.add(new BundleService.CreateItem(it.getQuestionId(), it.getOrderIndex(), it.getPointsOverride(), it.getNote()));
            }
        }
        QuestionBundle saved = bundleService.create(
                subjectId, createdByUserId, dto.getTitle(), dto.getInstructions(),
                items, dto.getTotalPoints(), dto.getStatus()
        );
        return mapper.toDto(saved);
    }

    // Update đơn giản: thay toàn bộ items theo dto
    @PutMapping("/{bundleId}")
    public QuestionBundleDTO update(@PathVariable Long subjectId,
                                    @PathVariable Long bundleId,
                                    @RequestBody BundleUpsertDTO dto) {
        QuestionBundle b = bundleRepo.findById(bundleId).orElseThrow();

        // update bundle fields
        b.setTitle(dto.getTitle());
        b.setInstructions(dto.getInstructions());
        b.setTotalPoints(dto.getTotalPoints());
        if (dto.getStatus() != null) b.setStatus(dto.getStatus());

        // Lấy danh sách item đang "active"
        List<BundleItem> active = itemRepo.findActiveByBundleIdOrderByOrderIndexAsc(bundleId);

        // Map theo questionId để tiện so khớp
        Map<Long, BundleItem> byQid = active.stream()
                .filter(it -> it.getQuestion() != null && it.getQuestion().getId() != null)
                .collect(Collectors.toMap(it -> it.getQuestion().getId(), it -> it));

        // Tập qid mới từ DTO
        List<BundleUpsertItemDTO> in = (dto.getItems() == null) ? List.of() : dto.getItems();
        var incomingQids = in.stream().map(BundleUpsertItemDTO::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 1) Soft-delete các item không còn trong DTO
        for (BundleItem it : active) {
            Long qid = it.getQuestion() == null ? null : it.getQuestion().getId();
            if (qid == null || !incomingQids.contains(qid)) {
                it.setIsDeleted(true);
                // có thể reset orderIndex nếu muốn: it.setOrderIndex(null);
                itemRepo.save(it);
            }
        }

        // 2) Upsert các item có trong DTO (update nếu tồn tại, tạo mới nếu chưa có)
        for (BundleUpsertItemDTO it : in) {
            BundleItem exist = byQid.get(it.getQuestionId());
            if (exist != null) {
                // revive nếu lỡ bị xóa mềm
                exist.setIsDeleted(false);
                exist.setOrderIndex(it.getOrderIndex());
                exist.setPointsOverride(it.getPointsOverride());
                exist.setNote(it.getNote());
                itemRepo.save(exist);
            } else {
                BundleItem ni = new BundleItem();
                ni.setBundle(b);
                var qRef = new com.exam.examserver.model.exam.Question(); // ref by id
                qRef.setId(it.getQuestionId());
                ni.setQuestion(qRef);
                ni.setOrderIndex(it.getOrderIndex());
                ni.setPointsOverride(it.getPointsOverride());
                ni.setNote(it.getNote());
                ni.setIsDeleted(false);
                itemRepo.save(ni);
            }
        }

        // Lưu bundle
        QuestionBundle saved = bundleRepo.save(b);
        return mapper.toDto(saved);
    }

    @DeleteMapping("/{bundleId}")
    public void delete(@PathVariable Long subjectId, @PathVariable Long bundleId) {
        bundleRepo.deleteById(bundleId);
    }

    @GetMapping("/lookup")
    public List<QuestionBundleDTO> lookupBundles(@PathVariable Long subjectId,
                                                 @RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        // giữ thứ tự theo ids
        Map<Long, QuestionBundle> map = bundleRepo.findAllById(ids).stream()
                .filter(b -> b.getSubject() != null && Objects.equals(b.getSubject().getId(), subjectId))
                .collect(Collectors.toMap(QuestionBundle::getId, b -> b));
        List<QuestionBundleDTO> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            QuestionBundle b = map.get(id);
            if (b != null) out.add(mapper.toDto(b));
        }
        return out;
    }
}
