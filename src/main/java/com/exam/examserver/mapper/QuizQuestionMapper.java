package com.exam.examserver.mapper;

import com.exam.examserver.dto.exam.QuizQuestionDTO;
import com.exam.examserver.model.exam.QuizQuestion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = QuestionMapper.class)
public interface QuizQuestionMapper {

    @Mapping(source = "orderIndex", target = "orderIndex")
    @Mapping(source = "question", target = "question")
    QuizQuestionDTO toDto(QuizQuestion quizQuestion);

    List<QuizQuestionDTO> toDtoList(Set<QuizQuestion> quizQuestions);
}

