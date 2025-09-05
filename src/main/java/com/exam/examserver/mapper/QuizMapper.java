package com.exam.examserver.mapper;

import com.exam.examserver.dto.exam.AddQuizQuestionDTO;
import com.exam.examserver.dto.exam.CreateQuizDTO;
import com.exam.examserver.dto.exam.QuizDTO;
import com.exam.examserver.model.exam.Quiz;
import com.exam.examserver.model.exam.QuizQuestion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class, QuizQuestionMapper.class, QuestionMapper.class})
public interface QuizMapper {

    @Mapping(target = "createdBy",
            source = "createdBy",
            qualifiedByName = "mapToUserBasicDto")
    @Mapping(source = "quizQuestions", target = "questions")
    QuizDTO toDto(Quiz quiz);

    @Mapping(target = "quizQuestions", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Quiz toEntity(CreateQuizDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quiz", ignore = true)
    @Mapping(target = "question", ignore = true) // Sẽ set trong service
    @Mapping(source = "orderIndex", target = "orderIndex") // Đảm bảo giữ nguyên orderIndex
    QuizQuestion toQuizQuestionEntity(AddQuizQuestionDTO dto);

    List<QuizDTO> toDtoList(List<Quiz> quizzes);
}