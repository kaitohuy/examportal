// src/main/java/com/exam/examserver/service/auto/AutoGenStepsJsonConverter.java
package com.exam.examserver.service.auto;

import com.exam.examserver.dto.autogen.AutoGenStepDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.util.List;

@Converter(autoApply = false)
public class AutoGenStepsJsonConverter implements AttributeConverter<List<AutoGenStepDTO>, PGobject> {

    private static final ObjectMapper M = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(List<AutoGenStepDTO> attribute) {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            String json = M.writeValueAsString(attribute == null ? List.of() : attribute);
            pg.setValue(json);
            return pg;
        } catch (Exception e) {
            throw new RuntimeException("Convert steps -> jsonb failed", e);
        }
    }

    @Override
    public List<AutoGenStepDTO> convertToEntityAttribute(PGobject dbData) {
        try {
            if (dbData == null || dbData.getValue() == null || dbData.getValue().isBlank()) {
                return List.of();
            }
            return M.readValue(dbData.getValue(), new TypeReference<List<AutoGenStepDTO>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Convert jsonb -> steps failed", e);
        }
    }
}
