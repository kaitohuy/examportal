package com.exam.examserver.service.dup;

import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionFingerprint;
import com.exam.examserver.repo.QuestionFingerprintRepository;
import com.exam.examserver.util.simhash.ProbeTokenizer;
import com.exam.examserver.util.simhash.SimHash64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

@Service
public class FingerprintService {

    private final QuestionFingerprintRepository repo;

    public FingerprintService(QuestionFingerprintRepository repo) {
        this.repo = repo;
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
        // isRoot: parent == null
        boolean isRoot = (q.getParent() == null);
        String probe = buildProbeFromQuestion(q);

        FP fp = build(probe);
        QuestionFingerprint ent = repo.findById(q.getId()).orElse(new QuestionFingerprint());
        ent.setQuestionId(q.getId());
        ent.setSubjectId(q.getSubject().getId());
        ent.setRoot(isRoot);
        ent.setTokenCount(fp.tokenCount());
        ent.setSimhash64(fp.simhash());
        ent.setCrc32(fp.crc32());
        ent.setB1(fp.b1()); ent.setB2(fp.b2()); ent.setB3(fp.b3()); ent.setB4(fp.b4());
        repo.save(ent);
    }

    public static String buildProbeFromQuestion(Question dtoOrEntity) {
        // dùng trực tiếp entity để tránh map vòng
        if (dtoOrEntity.getQuestionType() == null) return dtoOrEntity.getContent();
        switch (dtoOrEntity.getQuestionType()) {
            case MULTIPLE_CHOICE:
                StringBuilder sb = new StringBuilder();
                if (dtoOrEntity.getContent() != null) sb.append(dtoOrEntity.getContent()).append(' ');
                if (dtoOrEntity.getOptionA() != null) sb.append(" a) ").append(dtoOrEntity.getOptionA());
                if (dtoOrEntity.getOptionB() != null) sb.append(" b) ").append(dtoOrEntity.getOptionB());
                if (dtoOrEntity.getOptionC() != null) sb.append(" c) ").append(dtoOrEntity.getOptionC());
                if (dtoOrEntity.getOptionD() != null) sb.append(" d) ").append(dtoOrEntity.getOptionD());
                return sb.toString();
            case ESSAY:
                return (dtoOrEntity.getContent() == null ? "" : dtoOrEntity.getContent()) + "\n"
                        + (dtoOrEntity.getAnswerText() == null ? "" : dtoOrEntity.getAnswerText());
            default:
                return dtoOrEntity.getContent();
        }
    }

    /** Lấy candidate id qua 4 band; perBandLimit nên để 200 (tuỳ dữ liệu). */
    public List<Long> candidates(long subjectId, FP fp, int perBandLimit) {
        // Vì repository không có limit, ta tự cắt top N sau khi lấy (đơn giản).
        List<Long> out = new ArrayList<>(512);
        var l1 = repo.findByBand1(subjectId, fp.b1());
        var l2 = repo.findByBand2(subjectId, fp.b2());
        var l3 = repo.findByBand3(subjectId, fp.b3());
        var l4 = repo.findByBand4(subjectId, fp.b4());
        if (!l1.isEmpty()) out.addAll(l1.subList(0, Math.min(perBandLimit, l1.size())));
        if (!l2.isEmpty()) out.addAll(l2.subList(0, Math.min(perBandLimit, l2.size())));
        if (!l3.isEmpty()) out.addAll(l3.subList(0, Math.min(perBandLimit, l3.size())));
        if (!l4.isEmpty()) out.addAll(l4.subList(0, Math.min(perBandLimit, l4.size())));
        // unique
        return new ArrayList<>(new LinkedHashSet<>(out));
    }
}
