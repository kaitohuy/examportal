package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuestionImageRepository extends JpaRepository<QuestionImage, Long> {
    List<QuestionImage> findByQuestionIdOrderByOrderIndexAsc(Long questionId);

    void deleteByQuestionId(Long questionId);

    /** Lấy toàn bộ URL ảnh gallery theo danh sách questionId. */
    @Query("select i.url from QuestionImage i where i.question.id in :ids")
    List<String> findUrlsByQuestionIds(@Param("ids") Collection<Long> ids);

    /** Xoá gallery theo danh sách questionId (để tránh vướng FK). */
    @Modifying
    @Query("delete from QuestionImage i where i.question.id in :ids")
    int deleteByQuestionIdIn(@Param("ids") Collection<Long> ids);

    // QuestionImageRepository.java
    @Query("select qi.url from QuestionImage qi " +
            "where qi.question.id in :ids and qi.url is not null and qi.url <> ''")
    List<String> findAllUrlsByQuestionIds(@Param("ids") Collection<Long> ids);

    @Query("select count(qi) from QuestionImage qi " +
            "where qi.url = :url and qi.question.id not in :excludedIds")
    long countOtherGalleryRefs(@Param("url") String url,
                               @Param("excludedIds") Collection<Long> excludedIds);
}
