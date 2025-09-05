package com.exam.examserver.dto.importing;

import java.util.List;

public class ExtractResult {
    private final String text;
    private final List<byte[]> images;

    public ExtractResult(String text, List<byte[]> images) {
        this.text = text;
        this.images = images;
    }

    public String getText() {
        return text;
    }

    public List<byte[]> getImages() {
        return images;
    }


}
