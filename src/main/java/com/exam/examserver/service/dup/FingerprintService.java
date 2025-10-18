package com.exam.examserver.service.dup;

import com.exam.examserver.model.exam.BundleFingerprint;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.repo.BundleFingerprintRepository;
import com.exam.examserver.repo.QuestionFingerprintRepository;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.QuestionBundleRepository; // cần thêm
import com.exam.examserver.util.simhash.ProbeTokenizer;
import com.exam.examserver.util.simhash.SimHash64;
import com.exam.examserver.util.simhash.TfidfCosine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

@Service
public class FingerprintService {

    private final QuestionFingerprintRepository qRepo;
    private final BundleFingerprintRepository bRepo;
    private final QuestionBundleRepository bundleRepo;   // NEW
    private final QuestionRepository questionRepo;       // NEW

    public FingerprintService(QuestionFingerprintRepository qRepo,
                              BundleFingerprintRepository bRepo,
                              QuestionBundleRepository bundleRepo,
                              QuestionRepository questionRepo) {
        this.qRepo = qRepo;
        this.bRepo = bRepo;
        this.bundleRepo = bundleRepo;
        this.questionRepo = questionRepo;
    }

    public static record FP(long simhash, int b1, int b2, int b3, int b4, int tokenCount, int crc32) {}

    public FP build(String probe) {
        List<String> tokens = ProbeTokenizer.toTokens(probe);
        long h = SimHash64.compute(tokens);
        int b1 = SimHash64.band(h,0), b2 = SimHash64.band(h,1),
                b3 = SimHash64.band(h,2), b4 = SimHash64.band(h,3);

        CRC32 c = new CRC32();
        c.update(probe == null ? new byte[0] : probe.getBytes(StandardCharsets.UTF_8));
        int crc32 = (int) c.getValue();

        return new FP(h, b1, b2, b3, b4, tokens.size(), crc32);
    }

    @Transactional
    public void upsert(Question q) {
        boolean isRoot = (q.getParent() == null);
        String probe = buildProbeFromQuestion(q);

        FP fp = build(probe);
        var ent = qRepo.findById(q.getId()).orElse(new com.exam.examserver.model.exam.QuestionFingerprint());
        ent.setQuestionId(q.getId());
        ent.setSubjectId(q.getSubject().getId());
        ent.setRoot(isRoot);
        ent.setTokenCount(fp.tokenCount());
        ent.setSimhash64(fp.simhash());
        ent.setCrc32(fp.crc32());
        ent.setB1(fp.b1()); ent.setB2(fp.b2()); ent.setB3(fp.b3()); ent.setB4(fp.b4());
        qRepo.save(ent);
    }

    public static String buildProbeFromQuestion(Question q) {
        if (q.getQuestionType() == null) return q.getContent();
        switch (q.getQuestionType()) {
            case MULTIPLE_CHOICE:
                StringBuilder sb = new StringBuilder();
                if (q.getContent() != null) sb.append(q.getContent()).append(' ');
                if (q.getOptionA() != null) sb.append(" a) ").append(q.getOptionA());
                if (q.getOptionB() != null) sb.append(" b) ").append(q.getOptionB());
                if (q.getOptionC() != null) sb.append(" c) ").append(q.getOptionC());
                if (q.getOptionD() != null) sb.append(" d) ").append(q.getOptionD());
                return sb.toString();
            case ESSAY:
                return (q.getContent() == null ? "" : q.getContent()) + "\n"
                        + (q.getAnswerText() == null ? "" : q.getAnswerText());
            default:
                return q.getContent();
        }
    }

    /** Lấy candidate question id qua 4 band; perBandLimit nên để 200. */
    public List<Long> candidates(long subjectId, FP fp, int perBandLimit) {
        List<Long> out = new ArrayList<>(512);
        var l1 = qRepo.findByBand1(subjectId, fp.b1());
        var l2 = qRepo.findByBand2(subjectId, fp.b2());
        var l3 = qRepo.findByBand3(subjectId, fp.b3());
        var l4 = qRepo.findByBand4(subjectId, fp.b4());
        if (!l1.isEmpty()) out.addAll(l1.subList(0, Math.min(perBandLimit, l1.size())));
        if (!l2.isEmpty()) out.addAll(l2.subList(0, Math.min(perBandLimit, l2.size())));
        if (!l3.isEmpty()) out.addAll(l3.subList(0, Math.min(perBandLimit, l3.size())));
        if (!l4.isEmpty()) out.addAll(l4.subList(0, Math.min(perBandLimit, l4.size())));
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    // -------------------- BUNDLE --------------------

    /** Probe cho bundle: stem + từng part, mỗi phần xuống dòng để ổn định. */
    public String buildBundleProbe(String stemOrNull, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        if (stemOrNull != null && !stemOrNull.isBlank()) sb.append(stemOrNull.trim()).append('\n');
        if (parts != null) for (String p : parts) {
            if (p != null && !p.isBlank()) sb.append(p.trim()).append('\n');
        }
        return sb.toString().trim();
    }

    @Transactional
    public void upsertBundle(long bundleId, long subjectId, String probe) {
        FP fp = build(probe);
        var ent = bRepo.findById(bundleId).orElse(new BundleFingerprint());
        ent.setBundleId(bundleId);
        ent.setSubjectId(subjectId);
        ent.setTokenCount(fp.tokenCount());
        ent.setSimhash64(fp.simhash());
        ent.setCrc32(fp.crc32());
        ent.setB1(fp.b1()); ent.setB2(fp.b2()); ent.setB3(fp.b3()); ent.setB4(fp.b4());
        bRepo.save(ent);
    }

    /** Candidate bundle theo LSH bands. */
    public List<Long> bundleCandidates(long subjectId, FP fp, int perBandLimit) {
        List<Long> out = new ArrayList<>(512);
        var l1 = bRepo.findByBand1(subjectId, fp.b1());
        var l2 = bRepo.findByBand2(subjectId, fp.b2());
        var l3 = bRepo.findByBand3(subjectId, fp.b3());
        var l4 = bRepo.findByBand4(subjectId, fp.b4());
        if (!l1.isEmpty()) out.addAll(l1.subList(0, Math.min(perBandLimit, l1.size())));
        if (!l2.isEmpty()) out.addAll(l2.subList(0, Math.min(perBandLimit, l2.size())));
        if (!l3.isEmpty()) out.addAll(l3.subList(0, Math.min(perBandLimit, l3.size())));
        if (!l4.isEmpty()) out.addAll(l4.subList(0, Math.min(perBandLimit, l4.size())));
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    /** Lấy simhash64 cho danh sách bundleIds (dùng nhanh khi chỉ cần Hamming). */
    public Map<Long, Long> simhashByBundleIds(Collection<Long> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) return Collections.emptyMap();
        var rows = bRepo.findSimhashByBundleIds(bundleIds);
        Map<Long, Long> m = new HashMap<>(rows.size());
        for (Object[] r : rows) {
            Long id = ((Number) r[0]).longValue();
            Long sim = ((Number) r[1]).longValue();
            m.put(id, sim);
        }
        return m;
    }
// OLD METHOD
//    /** Dựng probe thực của bundle từ DB: instructions + từng question (giữ thứ tự). */
//    public String buildBundleProbeFromDb(Long bundleId) {
//        String instructions = bundleRepo.findInstructionsById(bundleId);    // NEW query
//        List<Long> qids = bundleRepo.findActiveQuestionIdsInBundle(bundleId);
//        if (qids == null) qids = List.of();
//        List<Question> qs = qids.isEmpty() ? List.of() : questionRepo.findAllById(qids);
//
//        // đảm bảo đúng thứ tự theo qids
//        Map<Long, Question> byId = new HashMap<>();
//        for (Question q : qs) byId.put(q.getId(), q);
//
//        StringBuilder sb = new StringBuilder();
//        if (instructions != null && !instructions.isBlank()) sb.append(instructions.trim()).append('\n');
//        for (Long id : qids) {
//            Question q = byId.get(id);
//            if (q == null) continue;
//            sb.append(buildProbeFromQuestion(q)).append('\n');
//        }
//        return sb.toString().trim();
//    }

    public String buildBundleProbeFromDb(Long bundleId) {
        String instructions = bundleRepo.findInstructionsById(bundleId);
        List<Long> qids = bundleRepo.findActiveQuestionIdsInBundle(bundleId);
        if (qids == null) qids = List.of();
        // OLD (comment): dùng findAllById -> sẽ lấy cả bản đã xóa mềm
        // List<Question> qs = qids.isEmpty() ? List.of() : questionRepo.findAllById(qids);

        // NEW: dùng findByIdIn -> đã lọc isDeleted=false
        List<Question> qs = qids.isEmpty() ? List.of() : questionRepo.findByIdIn(qids);

        Map<Long, Question> byId = new HashMap<>();
        for (Question q : qs) byId.put(q.getId(), q);

        StringBuilder sb = new StringBuilder();
        if (instructions != null && !instructions.isBlank()) sb.append(instructions.trim()).append('\n');
        for (Long id : qids) {
            Question q = byId.get(id);
            if (q == null) continue; // question bị xoá mềm -> bỏ qua
            sb.append(buildProbeFromQuestion(q)).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Lấy ứng viên bundle qua LSH, sau đó:
     * - Nếu Hamming ≤ 3  → score ≈ 0.95
     * - Nếu 4..6         → tính TF-IDF cosine giữa previewProbe và probe thật từ DB
     * - else             → 0
     *
     * Trả về: bundleId → score (0..1)
     */
    public Map<Long, Double> scoreBundleCandidates(long subjectId,
                                                   String previewBundleProbe,
                                                   FP previewBundleFp,
                                                   int perBandLimit) {
        List<Long> cand = bundleCandidates(subjectId, previewBundleFp, perBandLimit);
        if (cand.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> sim = simhashByBundleIds(cand);
        Map<Long, Double> out = new HashMap<>();

        for (Long bid : cand) {
            Long other = sim.get(bid);
            if (other == null) continue;

            int ham = SimHash64.hamming(previewBundleFp.simhash(), other);

            if (ham <= 3) {
                out.put(bid, 0.95); // “gần như chắc”
            } else if (ham <= 6) {
                try {
                    String otherProbe = buildBundleProbeFromDb(bid);
                    double cos = TfidfCosine.cosine(previewBundleProbe, otherProbe);
                    out.put(bid, cos);
                } catch (Exception e) {
                    // fallback an toàn
                    out.put(bid, 0.70);
                }
            } else {
                out.put(bid, 0.0);
            }
        }
        return out;
    }

    @Transactional
    public void remove(Long questionId) {
        try { qRepo.deleteById(questionId); } catch (Exception ignore) {}
    }

    @Transactional
    public void removeBundle(Long bundleId) {
        try { bRepo.deleteById(bundleId); } catch (Exception ignore) {}
    }

    @Transactional
    public void rebuildBundleFP(long bundleId, long subjectId,
                                String stem, List<String> parts) {
        String probe = buildBundleProbe(stem, parts);
        upsertBundle(bundleId, subjectId, probe);
    }
}
