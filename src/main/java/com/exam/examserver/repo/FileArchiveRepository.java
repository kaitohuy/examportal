// com.exam.examserver.repo.FileArchiveRepository
package com.exam.examserver.repo;

import com.exam.examserver.model.exam.FileArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FileArchiveRepository extends JpaRepository<FileArchive, Long>, JpaSpecificationExecutor<FileArchive> {
    // theo subject (đã có)
    Page<FileArchive> findBySubjectId(Long subjectId, Pageable p);
    Page<FileArchive> findBySubjectIdAndKind(Long subjectId, String kind, Pageable p);
    Page<FileArchive> findBySubjectIdAndFilenameContainingIgnoreCase(Long subjectId, String q, Pageable p);
    Page<FileArchive> findBySubjectIdAndKindAndFilenameContainingIgnoreCase(Long subjectId, String kind, String q, Pageable p);

    // toàn bộ subject
    Page<FileArchive> findByKind(String kind, Pageable p);
    Page<FileArchive> findByFilenameContainingIgnoreCase(String q, Pageable p);
    Page<FileArchive> findByKindAndFilenameContainingIgnoreCase(String kind, String q, Pageable p);
    Page<FileArchive> findByKindAndVariant(String kind, String variant, Pageable p);

}
