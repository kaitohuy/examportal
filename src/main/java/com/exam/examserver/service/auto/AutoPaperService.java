// src/main/java/com/exam/examserver/service/auto/AutoPaperService.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.*;
import com.exam.examserver.enums.*;
import com.exam.examserver.model.exam.QuestionMeta;
import com.exam.examserver.repo.QuestionBundleRepository;
import com.exam.examserver.repo.QuestionMetaRepository;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.spec.QuestionMetaSpecs;
import com.exam.examserver.service.QuestionMetaService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AutoPaperService {

    private final QuestionMetaRepository metaRepo;
    private final QuestionMetaService metaService;
    private final QuestionBundleRepository bundleRepo;
    private final QuestionRepository questionRepo;

    public AutoPaperService(QuestionMetaRepository metaRepo,
                            QuestionMetaService metaService,
                            QuestionBundleRepository bundleRepo,
                            QuestionRepository questionRepo) {
        this.metaRepo = metaRepo;
        this.metaService = metaService;
        this.bundleRepo = bundleRepo;
        this.questionRepo = questionRepo;
    }

    /* ========= PREVIEW ========= */
    public AutoGenPreviewResponse preview(Long subjectId, AutoGenRequest req) {
        Objects.requireNonNull(req);
        int N = Math.max(1, req.variants);

        // Parse label scope từ request
        LabelScope labelScope = parseLabels(req);

        // Tập questionId hợp lệ theo nhãn (để post-filter pool meta)
        Set<Long> allowedLabelIds;
        if (!labelScope.empty) {
            List<Long> ids = questionRepo.findApprovedIdsByScopeAndLabels(
                    subjectId, null, labelScope.enums, false
            );
            allowedLabelIds = new HashSet<>(ids);
        } else {
            allowedLabelIds = null;
        }

        // --- Default steps ---
        if (req.steps == null || req.steps.isEmpty()) {
            List<AutoGenStepDTO> defaults = new ArrayList<>();

            // Câu 1: 02 ý — 1đ Ch1 + 1đ Ch5
            {
                AutoGenStepDTO s = new AutoGenStepDTO();
                s.title = "Câu 1: 1đ Ch.1 + 1đ Ch.5";
                s.selectors = List.of(
                        AutoGenSelectorDTO.chapter(1, new BigDecimal("1.00")),
                        AutoGenSelectorDTO.chapter(5, new BigDecimal("1.00"))
                );
                defaults.add(s);
            }

            // Câu 2: bundle typeCode 2.1
            {
                AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
                sel.unitKind  = UnitKind.FULL_QUESTION;
                sel.chapterIn = List.of(2);
                sel.pointsEq  = new BigDecimal("2.00");
                sel.typeCodeIn = List.of("2.1");
                sel.status    = RecordStatus.APPROVED;
                AutoGenStepDTO s = new AutoGenStepDTO();
                s.title = "Câu 2: 2đ kiểu 2.1";
                s.selectors = List.of(sel);
                defaults.add(s);
            }

            // Câu 3: bundle typeCode 2.2
            {
                AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
                sel.unitKind  = UnitKind.FULL_QUESTION;
                sel.chapterIn = List.of(2);
                sel.pointsEq  = new BigDecimal("2.00");
                sel.typeCodeIn = List.of("2.2");
                sel.status    = RecordStatus.APPROVED;
                AutoGenStepDTO s = new AutoGenStepDTO();
                s.title = "Câu 3: 2đ kiểu 2.2";
                s.selectors = List.of(sel);
                defaults.add(s);
            }

            // Câu 4: 2 ý trong chương 3 — THEORY + EXERCISE
            {
                AutoGenSelectorDTO a = AutoGenSelectorDTO.chapter(3, new BigDecimal("1.00"));
                a.nature = ItemNature.THEORY;
                AutoGenSelectorDTO b = AutoGenSelectorDTO.chapter(3, new BigDecimal("1.00"));
                b.nature = ItemNature.EXERCISE;
                AutoGenStepDTO s = new AutoGenStepDTO();
                s.title = "Câu 4: 1đ Lý thuyết+ 1đ Ứng dụng (Ch.3)";
                s.selectors = List.of(a, b);
                defaults.add(s);
            }

            // Câu 5: bundle theo chương 4
            {
                AutoGenSelectorDTO sel = new AutoGenSelectorDTO();
                sel.unitKind  = UnitKind.FULL_QUESTION;
                sel.chapterIn = List.of(4);
                sel.pointsEq  = new BigDecimal("2.00");
                sel.status    = RecordStatus.APPROVED;
                AutoGenStepDTO s = new AutoGenStepDTO();
                s.title = "Câu 5: 2đ Ch.4";
                s.selectors = List.of(sel);
                defaults.add(s);
            }

            req.steps = defaults;
        }

        AutoGenPreviewResponse out = new AutoGenPreviewResponse();
        out.variants = N;
        out.rows = new ArrayList<>();
        out.paperQuestionIds = new ArrayList<>();
        out.paperTotals = new BigDecimal[N];
        Arrays.fill(out.paperTotals, BigDecimal.ZERO);

        Set<Long> globalUsed = req.noRepeatAcrossPapers ? new HashSet<>() : Collections.emptySet();
        for (int k = 0; k < N; k++) out.paperQuestionIds.add(new ArrayList<>());

        // ==== MAIN LOOP ====
        for (int stepIdx = 0; stepIdx < req.steps.size(); stepIdx++) {
            AutoGenStepDTO step = req.steps.get(stepIdx);
            AutoGenRowDTO row = new AutoGenRowDTO();
            row.title = (step.title == null ? ("Câu " + (stepIdx + 1)) : step.title);
            row.columns = new ArrayList<>(N);

            for (int k = 0; k < N; k++) {
                Set<Long> usedInPaper = req.noRepeatWithinPaper ? new HashSet<>(out.paperQuestionIds.get(k)) : Collections.emptySet();

                List<Long> pickedForCell = new ArrayList<>();
                BigDecimal sumPts = BigDecimal.ZERO;

                if (step.selectors == null || step.selectors.isEmpty()) {
                    out.errors.add("Step#" + (stepIdx+1) + " thiếu selectors");
                    row.columns.add(emptyCell());
                    continue;
                }

                boolean starvation = false;

                for (int selIdx = 0; selIdx < step.selectors.size(); selIdx++) {
                    AutoGenSelectorDTO sel = step.selectors.get(selIdx);

                    if (sel.unitKind == UnitKind.FULL_QUESTION) {
                        BigDecimal inc;
                        if (sel.typeCodeIn != null && !sel.typeCodeIn.isEmpty()) {
                            inc = handleBundleByType(subjectId, sel, stepIdx, selIdx,
                                    usedInPaper, globalUsed, pickedForCell, out, labelScope);
                        } else if (sel.chapterIn != null && !sel.chapterIn.isEmpty()) {
                            inc = handleBundleByChapter(subjectId, sel, stepIdx, selIdx,
                                    usedInPaper, globalUsed, pickedForCell, out, labelScope);
                        } else {
                            inc = handleSingleFull(subjectId, sel, stepIdx, selIdx,
                                    usedInPaper, globalUsed, pickedForCell, out, allowedLabelIds);
                        }
                        if (inc == null) { starvation = true; break; }
                        sumPts = sumPts.add(inc);
                        continue;
                    }

                    // ========= CASE B: SUB_ITEM =========
                    Specification<QuestionMeta> spec = buildSpec(subjectId, sel);
                    List<QuestionMeta> pool = metaRepo.findAll(spec);
                    if (allowedLabelIds != null) {
                        pool = pool.stream()
                                .filter(m -> allowedLabelIds.contains(m.getQuestionId()))
                                .toList();
                    }
                    if (pool.isEmpty()) {
                        out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " không có item nào.");
                        starvation = true; break;
                    }

                    Set<Long> banned = new HashSet<>();
                    banned.addAll(usedInPaper);
                    banned.addAll(globalUsed);
                    banned.addAll(pickedForCell);

                    List<QuestionMeta> candidates = pool.stream()
                            .filter(m -> !banned.contains(m.getQuestionId()))
                            .collect(Collectors.toList());
                    if (candidates.isEmpty()) {
                        out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " hết item do no-repeat.");
                        starvation = true; break;
                    }

                    QuestionMeta chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                    pickedForCell.add(chosen.getQuestionId());
                    sumPts = sumPts.add(chosen.getPoints() == null ? BigDecimal.ZERO : chosen.getPoints());
                }

                if (starvation) { row.columns.add(emptyCell()); continue; }

                out.paperQuestionIds.get(k).addAll(pickedForCell);
                row.columns.add(cellOf(pickedForCell, sumPts));
                out.paperTotals[k] = out.paperTotals[k].add(sumPts);

                if (req.noRepeatAcrossPapers) globalUsed.addAll(pickedForCell);
            }

            out.rows.add(row);
        }

        return out;
    }

    private BigDecimal handleSingleFull(Long subjectId, AutoGenSelectorDTO sel,
                                        int stepIdx, int selIdx,
                                        Set<Long> usedInPaper, Set<Long> globalUsed,
                                        List<Long> pickedForCell,
                                        AutoGenPreviewResponse out,
                                        Set<Long> allowedLabelIds) {
        Specification<QuestionMeta> spec = buildSpec(subjectId, sel);
        List<QuestionMeta> pool = metaRepo.findAll(spec);
        if (allowedLabelIds != null) {
            pool = pool.stream()
                    .filter(m -> allowedLabelIds.contains(m.getQuestionId()))
                    .toList();
        }
        if (pool.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (single full) không có item.");
            return null;
        }

        Set<Long> banned = new HashSet<>();
        banned.addAll(usedInPaper);
        banned.addAll(globalUsed);
        banned.addAll(pickedForCell);

        List<QuestionMeta> candidates = pool.stream()
                .filter(m -> !banned.contains(m.getQuestionId()))
                .toList();
        if (candidates.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (single full) hết do no-repeat.");
            return null;
        }

        QuestionMeta chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        pickedForCell.add(chosen.getQuestionId());
        return chosen.getPoints() == null ? BigDecimal.ZERO : chosen.getPoints();
    }

    private BigDecimal handleBundleByChapter(Long subjectId, AutoGenSelectorDTO sel,
                                             int stepIdx, int selIdx,
                                             Set<Long> usedInPaper, Set<Long> globalUsed,
                                             List<Long> pickedForCell,
                                             AutoGenPreviewResponse out,
                                             LabelScope labelScope) {
        Integer chapter = (sel.chapterIn != null && !sel.chapterIn.isEmpty()) ? sel.chapterIn.get(0) : null;
        BigDecimal minPts = (sel.pointsEq != null ? sel.pointsEq :
                (sel.pointsMin != null ? sel.pointsMin : BigDecimal.ZERO));
        BigDecimal maxPts = (sel.pointsEq != null ? sel.pointsEq :
                (sel.pointsMax != null ? sel.pointsMax : new BigDecimal("9999")));

        List<Long> bundleIds;
        if (labelScope.empty) {
            bundleIds = bundleRepo.findCandidateBundleIds(subjectId, chapter, minPts, maxPts);
        } else {
            bundleIds = bundleRepo.findCandidateBundleIdsByLabels(
                    subjectId, chapter, minPts, maxPts, labelScope.enums, false
            );
        }
        if (bundleIds.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (bundle by chapter) không có ứng viên.");
            return null;
        }

        Set<Long> banned = new HashSet<>();
        banned.addAll(usedInPaper);
        banned.addAll(globalUsed);
        banned.addAll(pickedForCell);

        List<Long> okBundles = new ArrayList<>();
        Map<Long, List<Long>> bundleItemsCache = new HashMap<>();

        for (Long bid : bundleIds) {
            List<Long> qids = bundleRepo.findQuestionIdsInBundle(bid);
            bundleItemsCache.put(bid, qids);
            if (qids.stream().anyMatch(banned::contains)) continue;
            okBundles.add(bid);
        }
        if (okBundles.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (bundle by chapter) hết do no-repeat.");
            return null;
        }

        Long chosenBid = okBundles.get(ThreadLocalRandom.current().nextInt(okBundles.size()));
        List<Long> qids = bundleItemsCache.get(chosenBid);
        pickedForCell.addAll(qids);

        return qids.stream()
                .map(qid -> metaRepo.findByQuestionId(qid).map(QuestionMeta::getPoints).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal handleBundleByType(Long subjectId, AutoGenSelectorDTO sel,
                                          int stepIdx, int selIdx,
                                          Set<Long> usedInPaper, Set<Long> globalUsed,
                                          List<Long> pickedForCell,
                                          AutoGenPreviewResponse out,
                                          LabelScope labelScope) {
        Integer chapter = (sel.chapterIn != null && !sel.chapterIn.isEmpty()) ? sel.chapterIn.get(0) : null;
        BigDecimal minPts = (sel.pointsEq != null ? sel.pointsEq :
                (sel.pointsMin != null ? sel.pointsMin : BigDecimal.ZERO));
        BigDecimal maxPts = (sel.pointsEq != null ? sel.pointsEq :
                (sel.pointsMax != null ? sel.pointsMax : new BigDecimal("9999")));

        List<Long> bundleIds;
        if (labelScope.empty) {
            bundleIds = bundleRepo.findCandidateBundleIds(subjectId, chapter, minPts, maxPts);
        } else {
            bundleIds = bundleRepo.findCandidateBundleIdsByLabels(
                    subjectId, chapter, minPts, maxPts, labelScope.enums, false
            );
        }
        if (bundleIds.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (bundle by type) không có ứng viên.");
            return null;
        }

        Set<Long> banned = new HashSet<>();
        banned.addAll(usedInPaper);
        banned.addAll(globalUsed);
        banned.addAll(pickedForCell);

        List<Long> okBundles = new ArrayList<>();
        Map<Long, List<Long>> bundleItems = new HashMap<>();

        for (Long bid : bundleIds) {
            List<Long> qids = bundleItems.computeIfAbsent(bid, bundleRepo::findQuestionIdsInBundle);
            if (qids.stream().anyMatch(banned::contains)) continue;

            boolean typeOk = qids.stream().allMatch(qid -> {
                var om = metaRepo.findByQuestionId(qid);
                String tc = om.isPresent() ? om.get().getTypeCode() : null;
                return matchesTypeCode(tc, sel.typeCodeIn);
            });
            if (typeOk) okBundles.add(bid);
        }
        if (okBundles.isEmpty()) {
            out.errors.add("Step#" + (stepIdx+1) + " selector#" + (selIdx+1) + " (bundle by type) hết do no-repeat/không khớp typeCode.");
            return null;
        }

        Long chosenBid = okBundles.get(ThreadLocalRandom.current().nextInt(okBundles.size()));
        List<Long> qids = bundleItems.get(chosenBid);
        pickedForCell.addAll(qids);

        return qids.stream()
                .map(qid -> metaRepo.findByQuestionId(qid).map(QuestionMeta::getPoints).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Helper: "2.1" sẽ match "2.1" và "2.1.1"
    private static boolean matchesTypeCode(String t, List<String> codes) {
        if (t == null || codes == null || codes.isEmpty()) return false;
        for (String c : codes) {
            if (t.equals(c) || t.startsWith(c + ".")) return true;
        }
        return false;
    }

    /* ========= COMMIT ========= */
    @Transactional
    public AutoGenPreviewResponse commit(Long subjectId, AutoGenRequest req) {
        AutoGenPreviewResponse resp = preview(subjectId, req);
        // mark used tất cả id đã chọn
        Set<Long> all = resp.paperQuestionIds.stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        metaService.markUsed(all);
        return resp;
    }

    /* ========= helpers ========= */

    private static AutoGenCellDTO emptyCell() {
        AutoGenCellDTO c = new AutoGenCellDTO();
        c.questionIds = List.of();
        c.totalPoints = BigDecimal.ZERO;
        return c;
    }

    private static AutoGenCellDTO cellOf(List<Long> ids, BigDecimal pts) {
        AutoGenCellDTO c = new AutoGenCellDTO();
        c.questionIds = ids;
        c.totalPoints = (pts == null ? BigDecimal.ZERO : pts);
        return c;
    }

    private static final class LabelScope {
        final boolean empty;
        final List<QuestionLabel> enums;
        LabelScope(boolean empty, List<QuestionLabel> enums) { this.empty = empty; this.enums = enums; }
    }

    private static LabelScope parseLabels(AutoGenRequest req) {
        if (req == null || req.labels == null || req.labels.isEmpty())
            return new LabelScope(true, List.of());
        List<QuestionLabel> list = req.labels.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(QuestionLabel::valueOf) // "EXAM"/"PRACTICE"
                .toList();
        return new LabelScope(list.isEmpty(), list);
    }

    private Specification<QuestionMeta> buildSpec(Long subjectId, AutoGenSelectorDTO sel) {
        List<Specification<QuestionMeta>> specs = new ArrayList<>();
        specs.add(QuestionMetaSpecs.bySubjectId(subjectId));
        if (sel.unitKind != null)          specs.add(QuestionMetaSpecs.unitKind(sel.unitKind));
        if (sel.cognitive != null)         specs.add(QuestionMetaSpecs.cognitive(sel.cognitive));
        if (sel.chapterIn != null && !sel.chapterIn.isEmpty())
            specs.add(QuestionMetaSpecs.chapterIn(sel.chapterIn));
        if (sel.typeCodeIn != null && !sel.typeCodeIn.isEmpty())
            specs.add(QuestionMetaSpecs.typeCodeIn(sel.typeCodeIn));
        if (sel.pointsEq != null)          specs.add(QuestionMetaSpecs.pointsBetween(sel.pointsEq, sel.pointsEq));
        else                                specs.add(QuestionMetaSpecs.pointsBetween(sel.pointsMin, sel.pointsMax));
        RecordStatus st = (sel.status != null ? sel.status : RecordStatus.APPROVED);
        specs.add(QuestionMetaSpecs.status(st));
        return Specification.allOf(specs);
    }
}
