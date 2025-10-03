package com.exam.examserver.mapper;
import com.exam.examserver.dto.exam.BundleItemDTO;
import com.exam.examserver.dto.exam.BundleUpsertDTO;
import com.exam.examserver.dto.exam.BundleUpsertItemDTO;
import com.exam.examserver.dto.exam.QuestionBundleDTO;
import org.mapstruct.*;
import java.util.List;
import com.exam.examserver.model.exam.*;

@Mapper(componentModel = "spring")
public interface QuestionBundleMapper {

    // Entity -> DTO
    @Mapping(source = "subject.id", target = "subjectId")
    @Mapping(source = "createdBy.id", target = "createdById")
    QuestionBundleDTO toDto(QuestionBundle entity);

    List<BundleItemDTO> toItemDtoList(List<BundleItem> items);

    @Mapping(source = "question.id", target = "questionId")
    BundleItemDTO toItemDto(BundleItem item);

    // Upsert DTO -> Entity (subject, createdBy, questions sẽ set ở service)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "items", ignore = true) // sẽ tự build ở service
    QuestionBundle toEntity(BundleUpsertDTO dto);

    // helper: map từ UpsertItemDTO sang BundleItem (question set ở service)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bundle", ignore = true)
    @Mapping(target = "question", ignore = true)
    BundleItem toEntity(BundleUpsertItemDTO dto);
}
