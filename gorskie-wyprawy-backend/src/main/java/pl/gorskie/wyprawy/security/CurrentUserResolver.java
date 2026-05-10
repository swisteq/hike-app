package pl.gorskie.wyprawy.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.gorskie.wyprawy.model.User;
import pl.gorskie.wyprawy.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono zalogowanego uzytkownika"));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
