package com.exam.examserver.service.impl;

import com.exam.examserver.model.PasswordResetToken;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.PasswordResetTokenRepository;
import com.exam.examserver.repo.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder;

    /** “From” hiển thị trong email, ví dụ: ExamPortal <no-reply@examportal.local> */
    @Value("${app.mail.from:no-reply@examportal.local}")
    private String fromHeader;

    /** Base URL FE để build link reset (ưu tiên dùng nếu có) */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepo,
            UserRepository userRepo,
            JavaMailSender mailSender,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    /** Tạo token và gửi mail. Luôn im lặng nếu email không tồn tại. */
    @Transactional
    public void createAndSendToken(String email, String appUrlFromRequest) {
        var userOpt = userRepo.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) return; // không lộ thông tin tồn tại user

        var user = userOpt.get();

        // raw token gửi cho user (KHÔNG lưu thô)
        var raw = UUID.randomUUID() + "-" + RandomStringUtils.randomAlphanumeric(32);
        var hash = DigestUtils.sha256Hex(raw);

        var prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setTokenHash(hash);
        prt.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
        tokenRepo.save(prt);

        // Ưu tiên base FE trong cấu hình
        String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : appUrlFromRequest;
        var link = base + "/reset-password?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);

        try {
            sendEmail(user, link);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Nếu lỗi gửi mail, có thể rollback token hoặc ghi log tùy ý
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không gửi được email", e);
        }
    }

    /** Xác nhận token và đặt mật khẩu mới */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var hash = DigestUtils.sha256Hex(rawToken);
        var token = tokenRepo.findFirstByTokenHashAndUsedFalse(hash)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalid or expired"));

        var u = token.getUser();
        u.setPassword(passwordEncoder.encode(newPassword));
        token.setUsed(true);

        userRepo.save(u);
        tokenRepo.save(token);
    }

    private void sendEmail(User user, String link) throws MessagingException, UnsupportedEncodingException {
        var mm = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(mm, false, "UTF-8");

        // Tách "Tên <email>" nếu có
        String displayName = null;
        String fromEmail = fromHeader;
        Matcher m = Pattern.compile("^\\s*(.*?)\\s*<([^>]+)>\\s*$").matcher(fromHeader);
        if (m.matches()) {
            displayName = m.group(1).trim();
            fromEmail = m.group(2).trim();
        }
        if (displayName != null && !displayName.isBlank()) {
            helper.setFrom(new InternetAddress(fromEmail, displayName));
        } else {
            helper.setFrom(fromEmail);
        }

        helper.setTo(user.getEmail());
        helper.setSubject("[ExamPortal] Đặt lại mật khẩu");

        String name = (user.getFirstName() != null && !user.getFirstName().isBlank())
                ? user.getFirstName()
                : user.getUsername();

        // Nội dung HTML đơn giản
        String html = """
            <p>Xin chào %s,</p>
            <p>Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản ExamPortal.</p>
            <p><a href="%s">Nhấn vào đây để đặt lại mật khẩu</a> (liên kết hết hạn sau 30 phút).</p>
            <p>Nếu không phải bạn, vui lòng bỏ qua email này.</p>
            """.formatted(escape(name), link);

        helper.setText(html, true); // true = HTML
        mailSender.send(mm);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Cleanup token đã dùng/hết hạn (chạy mỗi giờ). */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        var now = Instant.now();
        tokenRepo.deleteAllExpiredOrUsed(now);
    }
}
