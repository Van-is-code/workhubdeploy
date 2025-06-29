package gr3.workhub.service;

import gr3.workhub.entity.User;
import gr3.workhub.repository.UserRepository;
import gr3.workhub.dto.UserDTO;
import gr3.workhub.security.JwtTokenGenerator;
import gr3.workhub.security.JwtUtil;
import gr3.workhub.exception.EmailAlreadyExistsException;
import gr3.workhub.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private JwtTokenGenerator jwtTokenGenerator;
    @Autowired
    private EmailService emailService;

    public String registerRecruiter(UserDTO userDTO, String rawPassword) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = new User();
        user.setFullname(userDTO.getFullname());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(User.Role.recruiter);
        user.setStatus(User.Status.unverified);
        user.setCreatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenGenerator.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        emailService.sendActivationEmail(user.getEmail(), token);

        return token;
    }

    public String registerCandidate(UserDTO userDTO, String rawPassword) {
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = new User();
        user.setFullname(userDTO.getFullname());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(userDTO.getPhone());
        user.setRole(User.Role.candidate);
        user.setStatus(User.Status.unverified);
        user.setCreatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenGenerator.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        emailService.sendActivationEmail(user.getEmail(), token);

        return token;
    }

    public String login(String email, String rawPassword, User.Role role) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Invalid credentials"));
        if (!passwordEncoder.matches(rawPassword, user.getPassword()) || user.getRole() != role) {
            throw new RuntimeException("Invalid credentials");
        }
        if (user.getStatus() != User.Status.verified) {
            throw new RuntimeException("Account not activated");
        }
        // Pass user id as a claim in the JWT
        return jwtTokenGenerator.generateToken(user.getEmail(), user.getRole().name(), user.getId());
    }

    public String loginAutoRole(String email, String rawPassword) {
        System.out.println("Login attempt: email=" + email + ", password=" + rawPassword);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Invalid credentials"));
        System.out.println("User found: " + user.getEmail() + ", status=" + user.getStatus() + ", role=" + user.getRole());
        // Cho phép đăng nhập với mật khẩu plain text hoặc BCrypt (chỉ dùng cho môi trường dev/test)
        boolean passwordMatch = false;
        if (user.getPassword().startsWith("$2a") || user.getPassword().startsWith("$2b") || user.getPassword().startsWith("$2y")) {
            passwordMatch = passwordEncoder.matches(rawPassword, user.getPassword());
        } else {
            passwordMatch = rawPassword.equals(user.getPassword());
        }
        if (!passwordMatch) {
            System.out.println("Password mismatch!");
            throw new RuntimeException("Invalid credentials");
        }
        if (user.getStatus() != User.Status.verified) {
            System.out.println("Account not activated!");
            throw new RuntimeException("Account not activated");
        }
        System.out.println("Login success!");
        // Pass user id as a claim in the JWT
        return jwtTokenGenerator.generateToken(user.getEmail(), user.getRole().name(), user.getId());
    }

    public void activateUser(String token) {
        String email = jwtUtil.getUsernameFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
        user.setStatus(User.Status.verified);
        userRepository.save(user);
    }




    public User updateProfile(HttpServletRequest request, UserDTO userDTO) {
        Integer userId = tokenService.extractUserIdFromRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setFullname(userDTO.getFullname());
        user.setPhone(userDTO.getPhone());
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        return userRepository.save(user);
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Email not found"));
        String token = jwtTokenGenerator.generateToken(user.getEmail(), user.getRole().name(), user.getId());
        emailService.sendResetPasswordEmail(user.getEmail(), token);
    }

    public void resetPassword(String token, String newPassword) {
        String email = jwtUtil.getUsernameFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Invalid token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
    public void changePassword(HttpServletRequest request, String oldPassword, String newPassword) {
        Integer userId = tokenService.extractUserIdFromRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Old password incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}