// src/main/java/com/exam/examserver/service/auto/AutoPaperService.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.*;
import com.exam.examserver.enums.*;
import com.exam.examserver.model.exam.AutoPaperSetting; // CHANGED package
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
    private final AutoPaperSettingService settingService;

    public AutoPaperService(QuestionMetaRepository metaRepo,
                            QuestionMetaService metaService,
                            QuestionBundleRepository bundleRepo,
                            QuestionRepository questionRepo,
                            AutoPaperSettingService settingService) {
        this.metaRepo = metaRepo;
        this.metaService = metaService;
        this.bundleRepo = bundleRepo;
        this.questionRepo = questionRepo;
        this.settingService = settingService;
    }

    /* ========= PREVIEW ========= */
    public AutoGenPreviewResponse preview(Long subjectId, AutoGenRequest req, AutoSettingKind kind) {

        Objects.requireNonNull(req);
        AutoSettingKind effectiveKind =
                (kind == null ? AutoSettingKind.EXAM : kind);

        // Lấy setting theo subject + kind
        Optional<AutoPaperSetting> settingOpt =
                settingService.findBySubject(subjectId, effectiveKind);

        // Variants: request > setting > 1
        int N = Math.max(
                1,
                (req.variants > 0)
                        ? req.variants
                        : settingOpt.map(AutoPaperSetting::getVariants).orElse(1)
        );

        // No-repeat flags: request true OR setting true
        final boolean NO_WITHIN = req.noRepeatWithinPaper
                || settingOpt.map(AutoPaperSetting::getNoRepeatWithin).orElse(false);
        final boolean NO_ACROSS = req.noRepeatAcrossPapers
                || settingOpt.map(AutoPaperSetting::getNoRepeatAcross).orElse(false);

        // notUsedYears: setting > default(1); 0 = tắt filter
        final Integer effectiveNotUsedYears = settingOpt.map(AutoPaperSetting::getNotUsedYears).orElse(1);

        // Labels scope: lấy từ request; nếu rỗng → fallback setting.labelScope (Set<QuestionLabel>)
        LabelScope labelScope = parseLabels(req);
        if (labelScope.empty) {
            List<QuestionLabel> fromSetting = settingOpt
                    .map(AutoPaperSetting::getLabelScope)
                    .filter(set -> set != null && !set.isEmpty())
                    .map(set -> set.stream().toList())
                    .orElse(List.of());
            if (!fromSetting.isEmpty()) labelScope = new LabelScope(false, fromSetting);
        }

        // Nếu request không có steps → dùng steps từ setting nếu có
        if ((req.steps == null || req.steps.isEmpty())) {
            settingOpt.map(AutoPaperSetting::getSteps)
                    .filter(steps -> steps != null && !steps.isEmpty())
                    .ifPresent(steps -> req.steps = steps);
        }

        // Default steps nếu vẫn trống (không bắt buộc, nhưng giữ như fallback)
        if (req.steps == null || req.steps.isEmpty()) {
            req.steps = AutoPaperSettingDefaults.defaultSteps();
        }

        // Tập questionId hợp lệ theo nhãn
        Set<Long> allowedLabelIds;
        if (!labelScope.empty) {
            List<Long> ids = questionRepo.findApprovedIdsByScopeAndLabels(
                    subjectId, null, labelScope.enums, false
            );
            allowedLabelIds = new HashSet<>(ids);
        } else {
            allowedLabelIds = null;
        }

        AutoGenPreviewResponse out = new AutoGenPreviewResponse();
        out.variants = N;
        out.rows = new ArrayList<>();
        out.paperQuestionIds = new ArrayList<>();
        out.paperTotals = new BigDecimal[N];
        Arrays.fill(out.paperTotals, BigDecimal.ZERO);

        Set<Long> globalUsed = NO_ACROSS ? new HashSet<>() : Collections.emptySet();
        for (int k = 0; k < N; k++) out.paperQuestionIds.add(new ArrayList<>());

        // ==== MAIN LOOP ====
        for (int stepIdx = 0; stepIdx < req.steps.size(); stepIdx++) {
            AutoGenStepDTO step = req.steps.get(stepIdx);
            AutoGenRowDTO row = new AutoGenRowDTO();
            row.title = (step.title == null ? ("Câu " + (stepIdx + 1)) : step.title);
            row.columns = new ArrayList<>(N);

            for (int k = 0; k < N; k++) {
                Set<Long> usedInPaper = NO_WITHIN ? new HashSet<>(out.paperQuestionIds.get(k)) : Collections.emptySet();

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
                                    usedInPaper, globalUsed, pickedForCell, out, allowedLabelIds,
                                    effectiveNotUsedYears);
                        }
                        if (inc == null) { starvation = true; break; }
                        sumPts = sumPts.add(inc);
                        continue;
                    }

                    // ========= CASE B: SUB_ITEM =========
                    Specification<QuestionMeta> spec = buildSpec(subjectId, sel, effectiveNotUsedYears);
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

                if (NO_ACROSS) globalUsed.addAll(pickedForCell);
            }

            out.rows.add(row);
        }

        return out;
    }

    public AutoGenPreviewResponse preview(Long subjectId, AutoGenRequest req) {
        return preview(subjectId, req, AutoSettingKind.EXAM);
    }

    private BigDecimal handleSingleFull(Long subjectId, AutoGenSelectorDTO sel,
                                        int stepIdx, int selIdx,
                                        Set<Long> usedInPaper, Set<Long> globalUsed,
                                        List<Long> pickedForCell,
                                        AutoGenPreviewResponse out,
                                        Set<Long> allowedLabelIds,
                                        Integer notUsedYears) {
        Specification<QuestionMeta> spec = buildSpec(subjectId, sel, notUsedYears);
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

        List<Long> bundleIds = labelScope.empty
                ? bundleRepo.findCandidateBundleIds(subjectId, chapter, minPts, maxPts)
                : bundleRepo.findCandidateBundleIdsByLabels(subjectId, chapter, minPts, maxPts, labelScope.enums, false);

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
            List<Long> rawQids = bundleRepo.findActiveQuestionIdsInBundle(bid);
            List<Long> qids = questionRepo.findByIdIn(rawQids).stream().map(q -> q.getId()).toList();
            bundleItemsCache.put(bid, qids);
            if (qids.isEmpty()) continue;
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

        List<Long> bundleIds = labelScope.empty
                ? bundleRepo.findCandidateBundleIds(subjectId, chapter, minPts, maxPts)
                : bundleRepo.findCandidateBundleIdsByLabels(subjectId, chapter, minPts, maxPts, labelScope.enums, false);

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
            List<Long> rawQids = bundleRepo.findActiveQuestionIdsInBundle(bid);
            List<Long> qids = questionRepo.findByIdIn(rawQids).stream().map(q -> q.getId()).toList();
            bundleItems.put(bid, qids);
            if (qids.isEmpty()) continue;
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

    private static boolean matchesTypeCode(String t, List<String> codes) {
        if (t == null || codes == null || codes.isEmpty()) return false;
        for (String c : codes) {
            if (t.equals(c) || t.startsWith(c + ".")) return true;
        }
        return false;
    }

    /* ========= COMMIT ========= */
    @Transactional
    public AutoGenPreviewResponse commit(Long subjectId, AutoGenRequest req, AutoSettingKind kind) {
        return preview(subjectId, req, kind);
    }

    @Transactional
    public AutoGenPreviewResponse commit(Long subjectId, AutoGenRequest req) {
        return commit(subjectId, req, AutoSettingKind.EXAM);
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
                .map(QuestionLabel::valueOf)
                .toList();
        return new LabelScope(list.isEmpty(), list);
    }

    // Thêm tham số notUsedYears; chỉ áp dụng khi >0
    private Specification<QuestionMeta> buildSpec(Long subjectId, AutoGenSelectorDTO sel, Integer notUsedYears) {
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

        if (notUsedYears != null && notUsedYears > 0) {
            specs.add(QuestionMetaSpecs.notUsedWithinYears(notUsedYears));
        }

        return Specification.allOf(specs);
    }
}
