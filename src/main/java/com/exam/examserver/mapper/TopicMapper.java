package com.exam.examserver.mapper;
import com.exam.examserver.dto.exam.TopicDTO;
import com.exam.examserver.dto.exam.TopicUpsertDTO;
import org.mapstruct.*;
import com.exam.examserver.model.exam.Topic;

@Mapper(componentModel = "spring")
public interface TopicMapper {

    @Mapping(source = "subject.id", target = "subjectId")
    @Mapping(source = "parentTopic.id", target = "parentTopicId")
    TopicDTO toDto(Topic entity);

    // Upsert => Entity (subject, parentTopic set ở service)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "parentTopic", ignore = true)
    Topic toEntity(TopicUpsertDTO dto);
}

