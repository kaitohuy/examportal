package com.exam.examserver.mapper;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.exam.QuestionImageDTO;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface QuestionMapper {

    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "mapToUserBasicDto")
    @Mapping(target = "images", source = "images")
    // labels map thẳng
    @Mapping(target = "labels", source = "labels")
    @Mapping(target = "parentId", source = "parent.id")
    QuestionDTO toDto(Question question);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    // labels map thẳng từ DTO (nếu null sẽ xử lý ở service)
    @Mapping(target = "labels", source = "labels")
    Question toEntity(CreateQuestionDTO dto);

    List<QuestionDTO> toDtoList(List<Question> questions);

    QuestionImageDTO toImageDto(QuestionImage image);
    List<QuestionImageDTO> toImageDtoList(List<QuestionImage> images);
}




