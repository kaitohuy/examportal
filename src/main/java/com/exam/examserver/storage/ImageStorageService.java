package com.exam.examserver.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.*;

@Service
public class ImageStorageService {
    private final Cloudinary cloudinary;
    private final Tika tika = new Tika();

    public ImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /** Upload từ MultipartFile (giữ nguyên để tương thích) */
    public String storeImage(MultipartFile image, Long questionId) throws IOException {
        String publicId = buildPublicId(questionId);
        Map uploadResult = cloudinary.uploader().upload(
                image.getBytes(),
                ObjectUtils.asMap("public_id", publicId, "resource_type", "image")
        );
        return (String) uploadResult.get("secure_url");
    }

    /** Overload cho import: upload trực tiếp từ byte[] (đoán content-type) */
    public String storeImage(byte[] bytes, Long questionId, String filenameHint, String contentTypeHint) throws IOException {
        String publicId = buildPublicId(questionId);

        // Đoán mime nếu hint trống/null (hiện không bắt buộc truyền cho Cloudinary)
        String mime = (contentTypeHint != null && !contentTypeHint.isBlank())
                ? contentTypeHint
                : safeDetect(bytes);

        Map options = ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", "image"
        );
        Map uploadResult = cloudinary.uploader().upload(bytes, options);
        return (String) uploadResult.get("secure_url");
    }

    /** Xoá 1 ảnh theo URL Cloudinary (giữ nguyên folder 'questions/...'). */
    public void deleteImage(String imageUrl) {
        String publicId = extractCloudinaryPublicId(imageUrl);
        if (publicId == null) return; // không rõ public_id -> bỏ qua
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            // Không nên fail toàn bộ vì 1 ảnh xoá lỗi; log lại nếu cần
            e.printStackTrace();
        }
    }

    private String buildPublicId(Long questionId) {
        return "questions/" + questionId + "_" + System.currentTimeMillis();
    }

    private String safeDetect(byte[] bytes) {
        try {
            return tika.detect(bytes); // ví dụ: "image/png", "image/jpeg"
        } catch (Exception e) {
            return "image/png"; // fallback
        }
    }

    /** Rút public_id chuẩn từ secure_url Cloudinary, giữ nguyên folder. */
    private String extractCloudinaryPublicId(String url) {
        if (url == null) return null;

        // Cloudinary pattern: .../upload/(optional transforms)/v123456789/QUESTIONS/FILE_NAME.EXT
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx < 0) {
            // Fallback (ít chính xác)
            int lastSlash = url.lastIndexOf('/');
            int lastDot = url.lastIndexOf('.');
            if (lastSlash >= 0 && lastDot > lastSlash) {
                return url.substring(lastSlash + 1, lastDot);
            }
            return null;
        }

        String tail = url.substring(uploadIdx + "/upload/".length());
        // Bỏ transforms nếu có (chuỗi không có dấu chấm và không phải version)
        // và bỏ "v123456..." nếu có.
        String[] parts = tail.split("/");
        int start = 0;
        // Bỏ transforms (không có dấu chấm, không phải v<digits>)
        while (start < parts.length &&
                !parts[start].contains(".") &&
                !(parts[start].startsWith("v") && parts[start].substring(1).chars().allMatch(Character::isDigit))) {
            start++;
        }
        // Bỏ version v123...
        if (start < parts.length &&
                parts[start].startsWith("v") &&
                parts[start].substring(1).chars().allMatch(Character::isDigit)) {
            start++;
        }
        if (start >= parts.length) return null;

        String joined = String.join("/", Arrays.asList(parts).subList(start, parts.length));
        int dot = joined.lastIndexOf('.');
        return (dot >= 0) ? joined.substring(0, dot) : joined;
    }
}
