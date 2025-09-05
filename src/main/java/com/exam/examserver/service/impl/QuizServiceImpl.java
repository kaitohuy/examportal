package com.exam.examserver.service.impl;

import com.exam.examserver.dto.exam.*;
import com.exam.examserver.mapper.QuestionMapper;
import com.exam.examserver.mapper.QuizMapper;
import com.exam.examserver.mapper.QuizQuestionMapper;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.Quiz;
import com.exam.examserver.model.exam.QuizQuestion;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.repo.QuizRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.QuizService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepo;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;
    private final QuestionRepository questionRepo;
    private final QuizMapper mapper;
    private final QuestionMapper questionMapper;
    private final QuizQuestionMapper quizQuestionMapper;

    public QuizServiceImpl(QuizRepository quizRepo,
                           SubjectRepository subjectRepo,
                           UserRepository userRepo,
                           QuestionRepository questionRepo,
                           QuizMapper mapper,
                           QuestionMapper questionMapper,
                           QuizQuestionMapper quizQuestionMapper) {
        this.quizRepo = quizRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
        this.questionRepo = questionRepo;
        this.mapper = mapper;
        this.questionMapper = questionMapper;
        this.quizQuestionMapper = quizQuestionMapper;
    }

    @Override
    public List<QuizDTO> getAllBySubject(Long subjectId) {
        subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        return quizRepo.findBySubjectId(subjectId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public QuizDTO create(Long subjectId, CreateQuizDTO payload, Long creatorUserId) {
        Subject subject = subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        User creator = userRepo.findById(creatorUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Quiz quiz = mapper.toEntity(payload);
        quiz.setSubject(subject);
        quiz.setCreatedBy(creator);
        quiz.setCreatedAt(LocalDateTime.now());
        quiz.setNumOfQuestions(0);

        if (payload.getQuestions() != null && !payload.getQuestions().isEmpty()) {
            List<QuizQuestion> quizQuestions = new ArrayList<>(); // Sử dụng List để giữ thứ tự
            int autoIndex = 1;
            for (AddQuizQuestionDTO questionDTO : payload.getQuestions()) {
                Question question = questionRepo.findById(questionDTO.getQuestionId())
                        .orElseThrow(() -> new EntityNotFoundException("Question not found"));
                if (!question.getSubject().getId().equals(subjectId)) {
                    throw new IllegalArgumentException("Question does not belong to the specified subject");
                }
                QuizQuestion qq = mapper.toQuizQuestionEntity(questionDTO);
                qq.setQuiz(quiz);
                qq.setQuestion(question);
                qq.setOrderIndex(questionDTO.getOrderIndex() != null ? questionDTO.getOrderIndex() : autoIndex++); // Ưu tiên người dùng, nếu null thì tự động gán
                quizQuestions.add(qq);
            }
            // Sắp xếp theo orderIndex để đảm bảo thứ tự đúng (bao gồm cả giá trị do người dùng nhập)
            quizQuestions.sort(Comparator.comparingInt(QuizQuestion::getOrderIndex));
            quiz.setQuizQuestions(new LinkedHashSet<>(quizQuestions)); // Chuyển sang Set nếu cần duy trì tính duy nhất
            quiz.setNumOfQuestions(quizQuestions.size());
        }

        Quiz saved = quizRepo.save(quiz);
        return mapper.toDto(saved);
    }

    @Override
    public QuizDTO getById(Long quizId, Long subjectId) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));
        if (!quiz.getSubject().getId().equals(subjectId)) {
            throw new IllegalArgumentException("Quiz does not belong to the specified subject");
        }
        return mapper.toDto(quiz);
    }

    @Override
    public QuizDTO update(Long quizId, Long subjectId, CreateQuizDTO payload) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));
        if (!quiz.getSubject().getId().equals(subjectId)) {
            throw new IllegalArgumentException("Quiz does not belong to the specified subject");
        }

        // Cập nhật metadata
        quiz.setTitle(payload.getTitle());
        quiz.setDescription(payload.getDescription());
        quiz.setMaxScore(payload.getMaxScore());
        quiz.setTimeLimitMinutes(payload.getTimeLimitMinutes());

        Quiz updated = quizRepo.save(quiz);
        return mapper.toDto(updated);
    }

    @Override
    public void delete(Long quizId) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));
        quizRepo.delete(quiz);
        // Cascade + orphanRemoval sẽ tự xóa QuizQuestion
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByQuiz(Long quizId, boolean hideAnswer) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));

        return quiz.getQuizQuestions().stream()
                .sorted(Comparator.comparingInt(QuizQuestion::getOrderIndex))
                .map(qq -> {
                    Question question = qq.getQuestion();
                    QuestionDTO dto = questionMapper.toDto(question);
                    if (hideAnswer) {
                        dto.setAnswer(null);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // Phương thức mới: Thêm câu hỏi vào quiz
    public List<QuizQuestionDTO> addQuestionsToQuiz(Long quizId, Long subjectId, AddQuizQuestionsDTO payload, Long userId) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));

        if (!quiz.getSubject().getId().equals(subjectId)) {
            throw new IllegalArgumentException("Quiz does not belong to the specified subject");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Set<QuizQuestion> quizQuestions = quiz.getQuizQuestions();
        List<QuizQuestion> newQuizQuestions = new ArrayList<>();
        int autoIndex = quiz.getNumOfQuestions() + 1; // Bắt đầu từ số câu hỏi hiện tại

        for (AddQuizQuestionDTO questionDTO : payload.getQuestions()) {
            Question question = questionRepo.findById(questionDTO.getQuestionId())
                    .orElseThrow(() -> new EntityNotFoundException("Question not found"));
            if (!question.getSubject().getId().equals(subjectId)) {
                throw new IllegalArgumentException("Question does not belong to the specified subject");
            }

            boolean exists = quizQuestions.stream()
                    .anyMatch(qq -> qq.getQuestion().getId().equals(questionDTO.getQuestionId()));
            if (exists) {
                throw new IllegalArgumentException("Question already exists in quiz");
            }

            QuizQuestion qq = mapper.toQuizQuestionEntity(questionDTO);
            qq.setQuiz(quiz);
            qq.setQuestion(question);
            qq.setOrderIndex(questionDTO.getOrderIndex() != null ? questionDTO.getOrderIndex() : autoIndex++); // Ưu tiên người dùng, nếu null thì tự động gán
            newQuizQuestions.add(qq);
        }

        // Sắp xếp theo orderIndex trước khi thêm vào quizQuestions
        newQuizQuestions.sort(Comparator.comparingInt(QuizQuestion::getOrderIndex));
        quizQuestions.addAll(newQuizQuestions);
        quiz.setNumOfQuestions(quizQuestions.size());
        quizRepo.save(quiz);

        return quizQuestionMapper.toDtoList(new LinkedHashSet<>(newQuizQuestions));
    }

    // Phương thức mới: Xóa câu hỏi khỏi quiz
    public void removeQuestionFromQuiz(Long quizId, Long questionId) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));

        QuizQuestion quizQuestion = quiz.getQuizQuestions().stream()
                .filter(qq -> qq.getQuestion().getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Question not found in quiz"));

        quiz.getQuizQuestions().remove(quizQuestion);
        quiz.setNumOfQuestions(quiz.getQuizQuestions().size());
        quizRepo.save(quiz);
    }

    @Override
    public QuizDTO updateQuestions(Long quizId, Long subjectId, List<AddQuizQuestionDTO> questions, Long userId) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new EntityNotFoundException("Quiz not found"));

        if (!quiz.getSubject().getId().equals(subjectId)) {
            throw new IllegalArgumentException("Quiz does not belong to the specified subject");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Lấy danh sách hiện tại của quizQuestions
        Set<QuizQuestion> currentQuizQuestions = quiz.getQuizQuestions();

        // Xác định các questionId mới từ payload
        Set<Long> newQuestionIds = questions.stream()
                .map(AddQuizQuestionDTO::getQuestionId)
                .collect(Collectors.toSet());

        // Xóa các QuizQuestion không còn trong danh sách mới
        currentQuizQuestions.removeIf(qq -> !newQuestionIds.contains(qq.getQuestion().getId()));

        // Thêm hoặc cập nhật các QuizQuestion mới
        int autoIndex = 1; // Bắt đầu từ 1
        for (AddQuizQuestionDTO questionDTO : questions) {
            Question question = questionRepo.findById(questionDTO.getQuestionId())
                    .orElseThrow(() -> new EntityNotFoundException("Question not found"));
            if (!question.getSubject().getId().equals(subjectId)) {
                throw new IllegalArgumentException("Question does not belong to the specified subject");
            }

            // Tìm QuizQuestion hiện có (nếu có) để cập nhật orderIndex
            QuizQuestion existingQq = currentQuizQuestions.stream()
                    .filter(qq -> qq.getQuestion().getId().equals(questionDTO.getQuestionId()))
                    .findFirst()
                    .orElse(null);

            QuizQuestion qq;
            if (existingQq != null) {
                qq = existingQq; // Sử dụng lại thực thể hiện có
            } else {
                qq = new QuizQuestion(); // Tạo mới nếu không tồn tại
                qq.setQuiz(quiz);
                qq.setQuestion(question);
                currentQuizQuestions.add(qq); // Thêm vào tập hợp hiện tại
            }
            qq.setOrderIndex(questionDTO.getOrderIndex() != null ? questionDTO.getOrderIndex() : autoIndex++);
        }

        // Cập nhật số lượng câu hỏi
        quiz.setNumOfQuestions(currentQuizQuestions.size());

        Quiz updated = quizRepo.save(quiz);
        return mapper.toDto(updated);
    }

    @Override
    public Long saveQuestion(CreateQuestionDTO dto, Long subjectId, Long userId) {
        Subject subject = subjectRepo.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        User creator = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Question question = questionMapper.toEntity(dto);
        question.setSubject(subject);
        question.setCreatedBy(creator);
        question.setCreatedAt(LocalDateTime.now());

        Question savedQuestion = questionRepo.save(question);
        return savedQuestion.getId();
    }

    @Override
    public Question findDuplicateQuestion(QuestionDTO dto) {
        // So sánh nội dung (có thể dùng hash hoặc so sánh trực tiếp)
        return questionRepo.findFirstByContent(dto.getContent()); // Giả định có phương thức tùy chỉnh trong QuestionRepository
    }
}