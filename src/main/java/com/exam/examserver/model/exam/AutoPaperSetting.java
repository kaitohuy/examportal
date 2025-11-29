// src/main/java/com/exam/examserver/model/auto/AutoPaperSetting.java
package com.exam.examserver.model.exam;

import com.exam.examserver.dto.autogen.AutoGenStepDTO;
import com.exam.examserver.enums.AutoSettingKind;
import com.exam.examserver.enums.QuestionLabel;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "auto_paper_setting",
        uniqueConstraints = {
                // (subject_id, kind) là duy nhất (mỗi môn có 1 bản EXAM và 1 bản PRACTICE)
                @UniqueConstraint(name="uq_auto_paper_setting_subject_kind",
                        columnNames = {"subject_id","kind"})
        }
)
public class AutoPaperSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="subject_id", nullable=false)
    private Long subjectId;

    @Column(nullable=false, length=160)
    private String name;

    // Số biến thể đề muốn sinh mặc định cho môn (FE vẫn có thể override trong request)
    @Column(name="variants", nullable=false)
    private int variants = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="label_scope", columnDefinition = "jsonb")
    private Set<QuestionLabel> labelScope;

    @Column(name="no_repeat_within", nullable=false)
    private boolean noRepeatWithin = true;

    @Column(name="no_repeat_across", nullable=false)
    private boolean noRepeatAcross = false;

    @Column(name="not_used_years", nullable=false)
    private Integer notUsedYears = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="steps", columnDefinition = "jsonb", nullable=false)
    private List<AutoGenStepDTO> steps;

    @CreationTimestamp
    @Column(name="created_at", updatable = false)
    private Instant createdAt;

    @Column(name="created_by")
    private String createdBy;

    @UpdateTimestamp
    @Column(name="updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private AutoSettingKind kind = AutoSettingKind.EXAM;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getVariants() { return variants; }
    public void setVariants(int variants) { this.variants = variants; }

    public Set<QuestionLabel> getLabelScope() { return labelScope; }
    public void setLabelScope(Set<QuestionLabel> labelScope) { this.labelScope = labelScope; }

    public boolean getNoRepeatWithin() { return noRepeatWithin; }
    public void setNoRepeatWithin(boolean noRepeatWithin) { this.noRepeatWithin = noRepeatWithin; }

    public boolean getNoRepeatAcross() { return noRepeatAcross; }
    public void setNoRepeatAcross(boolean noRepeatAcross) { this.noRepeatAcross = noRepeatAcross; }

    public Integer getNotUsedYears() { return notUsedYears; }
    public void setNotUsedYears(Integer notUsedYears) { this.notUsedYears = notUsedYears; }

    public List<AutoGenStepDTO> getSteps() { return steps; }
    public void setSteps(List<AutoGenStepDTO> steps) { this.steps = steps; }

    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AutoSettingKind getKind() { return kind; }
    public void setKind(AutoSettingKind kind) { this.kind = kind; }
}
