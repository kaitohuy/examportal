package com.exam.examserver.mapper;
import com.exam.examserver.dto.exam.QuestionMetaDTO;
import com.exam.examserver.dto.exam.QuestionMetaUpsertDTO;
import org.mapstruct.*;
import com.exam.examserver.model.exam.QuestionMeta;

@Mapper(componentModel = "spring")
public interface QuestionMetaMapper {
    @Mapping(source = "question.id", target = "questionId")
    @Mapping(source = "topic.id", target = "topicId")
    QuestionMetaDTO toDto(QuestionMeta entity);

    @Mapping(target = "questionId", ignore = true)
    @Mapping(target = "question", ignore = true)
    @Mapping(target = "topic", ignore = true)
    QuestionMeta toEntity(QuestionMetaUpsertDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "question", ignore = true)
    @Mapping(target = "topic", ignore = true)
    void updateEntity(@MappingTarget QuestionMeta entity, QuestionMetaUpsertDTO dto);
}
