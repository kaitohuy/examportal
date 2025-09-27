package com.exam.examserver;

import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.service.UserService;
import com.exam.examserver.util.TextSim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class ExamserverApplication implements CommandLineRunner {

	@Autowired
	private UserService userService;

	public static void main(String[] args) {
		SpringApplication.run(ExamserverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("starting code");
	}

//	@Bean
//	CommandLineRunner backfillContentNorm(QuestionRepository repo) {
//		return args -> {
//			// chạy một lần thôi, xong thì comment lại
//			repo.findAll().forEach(q -> {
//				String packed = (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
//						? TextSim.packMultipleChoice(q.getContent(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD())
//						: q.getContent();
//				q.setContentNorm(TextSim.forSimilarity(packed));
//				repo.save(q);
//			});
//		};
//	}
//	@Bean
//	CommandLineRunner backfillContentMathNorm(QuestionRepository repo) {
//		return args -> {
//			final int PAGE_SIZE = 500;
//			long total = repo.count();
//			long done = 0;
//			int pageIdx = 0;
//			while (true) {
//				Page<Question> page = repo.findAll(PageRequest.of(pageIdx, PAGE_SIZE));
//				if (page.isEmpty()) break;
//
//				List<Question> toSave = new ArrayList<>(page.getContent().size());
//				for (Question q : page.getContent()) {
//					// probe: MC thì gói A–D, còn lại lấy content
//					String probe = (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
//							? TextSim.packMultipleChoice(q.getContent(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD())
//							: q.getContent();
//
//					// math-aware fingerprint
//					String mathNorm = TextSim.forSimilarityMath(probe);
//					q.setContentMathNorm(mathNorm);
//
//					// (tuỳ chọn) nếu bạn vẫn dùng text-only thì cập nhật khi đang null
//					if (q.getContentNorm() == null || q.getContentNorm().isBlank()) {
//						q.setContentNorm(TextSim.forSimilarity(probe));
//					}
//
//					toSave.add(q);
//				}
//
//				repo.saveAll(toSave);
//				if (!page.hasNext()) break;
//				pageIdx++;
//			}
//		};
//	}
}
