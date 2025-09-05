package com.exam.examserver.dto.importing;

import java.util.List;

public record PageDTO<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public static <T> PageDTO<T> from(org.springframework.data.domain.Page<T> p) {
        return new PageDTO<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
}

