package pl.gorskie.wyprawy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Imie jest wymagane")
        private String name;

        @Email(message = "Nieprawidlowy format email")
        @NotBlank(message = "Email jest wymagany")
        private String email;

        @NotBlank(message = "Haslo jest wymagane")
        @Size(min = 6, message = "Haslo musi miec co najmniej 6 znakow")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AuthResponse {
        private String token;
        private Long userId;
        private String name;
        private String email;
    }

    @Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class UserResponse {
        private Long id;
        private String name;
        private String email;

        public static UserResponse from(pl.gorskie.wyprawy.model.User user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .build();
        }
    }
}
