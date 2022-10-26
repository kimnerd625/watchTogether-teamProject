package com.watchtogether.server.users.service.impl;

import static com.watchtogether.server.exception.type.UserErrorCode.ALREADY_SIGNUP_EMAIL;
import static com.watchtogether.server.exception.type.UserErrorCode.ALREADY_SIGNUP_NICKNAME;
import static com.watchtogether.server.exception.type.UserErrorCode.ALREADY_VERIFY_EMAIL;
import static com.watchtogether.server.exception.type.UserErrorCode.EXPIRED_VERIFY_EMAIL_CODE;
import static com.watchtogether.server.exception.type.UserErrorCode.LEAVE_USER;
import static com.watchtogether.server.exception.type.UserErrorCode.NEED_VERIFY_EMAIL;
import static com.watchtogether.server.exception.type.UserErrorCode.NOT_FOUND_USER;
import static com.watchtogether.server.exception.type.UserErrorCode.WRONG_PASSWORD_USER;
import static com.watchtogether.server.exception.type.UserErrorCode.WRONG_VERIFY_EMAIL_CODE;
import static com.watchtogether.server.users.domain.type.Authority.USER;

import com.watchtogether.server.components.mail.MailComponents;
import com.watchtogether.server.exception.UserException;
import com.watchtogether.server.users.domain.dto.UserDto;
import com.watchtogether.server.users.domain.entitiy.User;
import com.watchtogether.server.users.domain.repository.UserRepository;
import com.watchtogether.server.users.domain.type.Authority;
import com.watchtogether.server.users.domain.type.UserStatus;
import com.watchtogether.server.users.service.UserService;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MailComponents mailComponents;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDto singUpUser(String email, String nickname, String password, Date birth) {

        boolean existEmail = userRepository.existsById(email.toLowerCase(Locale.ROOT));
        if (existEmail) {
            throw new UserException(ALREADY_SIGNUP_EMAIL);
        }

        boolean existNick = userRepository.existsByNickname(nickname);
        if (existNick) {
            throw new UserException(ALREADY_SIGNUP_NICKNAME);
        }

        // 패스워드 암호화
        String encodePassword = passwordEncoder.encode(password);

        // 인증 메일 고유식별번호
        String code = getRandomCode();

        User user = userRepository.save(User.builder()
            .email(email.toLowerCase(Locale.ROOT))
            .nickname(nickname)
            .password(encodePassword)
            .cash(0L)
            .birth(birth)
            .emailVerify(false)
            .emailVerifyCode(code)
            .emailVerifyExpiredDt(LocalDateTime.now().plusDays(1))
            .status(UserStatus.REQ)
            .roles(USER.getRoles())
            .build());

        sendAuthEmail(email, code);

        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public void verifyUser(String email, String code) {
        User user = userRepository.findById(email)
            .orElseThrow(() -> new UserException(NOT_FOUND_USER));

        if (user.isEmailVerify()) {
            throw new UserException(ALREADY_VERIFY_EMAIL);
        } else if (!user.getEmailVerifyCode().equals(code)) {
            throw new UserException(WRONG_VERIFY_EMAIL_CODE);
        } else if (user.getEmailVerifyExpiredDt().isBefore(LocalDateTime.now())) {
            throw new UserException(EXPIRED_VERIFY_EMAIL_CODE);
        }

        // 이메일 인증
        user.setEmailVerify(true);
        // 사용자 상태
        user.setStatus(UserStatus.ING);
    }

    @Override
    @Transactional
    public UserDto signInUser(String email, String password) {

        User user = userRepository.findById(email)
            .orElseThrow(() -> new UserException(NOT_FOUND_USER));

        if (user.getStatus().equals(UserStatus.LEAVE)) {
            throw new UserException(LEAVE_USER);
        } else if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UserException(WRONG_PASSWORD_USER);
        } else if (!user.isEmailVerify()) {
            throw new UserException(NEED_VERIFY_EMAIL);
        }

        // 마지막 로그인 날짜 저장
        user.setLastLoginDt(LocalDateTime.now());

        return UserDto.fromEntity(user);
    }

    /**
     * 인증 메일 전송
     *
     * @param email
     * @param code
     * @return
     */
    public boolean sendAuthEmail(String email, String code) {

        StringBuilder builder = new StringBuilder();
        String subject = "watchTogether 사이트 가입을 축하드립니다!";
        String text = builder.append("<p>안녕하세요.</p>")
            .append("<p>이메일 인증을 완료하기위해 아래 링크를 클릭해주세요!.</p>")
            .append("<div><a href='http://localhost:8081/api/users/sign-up/verify/?email=")
            .append(email)
            .append("&code=")
            .append(code)
            .append("'>가입완료</a><div>")
            .toString();

        return mailComponents.sendMail(email, subject, text);
    }

    /**
     * 문자와 숫자로 조합된 15자리의 코드 생성
     *
     * @return
     */
    private String getRandomCode() {
        return RandomStringUtils.random(15, true, true);
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException(NOT_FOUND_USER));
        return user;
    }
}
