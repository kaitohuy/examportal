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
        b.setTitle(dto.getTitle());
        b.setInstructions(dto.getInstructions());
        b.setTotalPoints(dto.getTotalPoints());
        b.setStatus(dto.getStatus() == null ? b.getStatus() : dto.getStatus());
        // replace items:
        List<BundleItem> old = itemRepo.findByBundleIdOrderByOrderIndexAsc(bundleId);
        itemRepo.deleteAll(old);
        if (dto.getItems() != null) {
            for (BundleUpsertItemDTO it : dto.getItems()) {
                BundleItem bi = new BundleItem();
                bi.setBundle(b);
                bi.setQuestion(new com.exam.examserver.model.exam.Question()); // ref by id
                bi.getQuestion().setId(it.getQuestionId());
                bi.setOrderIndex(it.getOrderIndex());
                bi.setPointsOverride(it.getPointsOverride());
                bi.setNote(it.getNote());
                itemRepo.save(bi);
            }
        }
        return mapper.toDto(bundleRepo.save(b));
    }

    @DeleteMapping("/{bundleId}")
    public void delete(@PathVariable Long subjectId, @PathVariable Long bundleId) {
        bundleRepo.deleteById(bundleId);
    }
}
