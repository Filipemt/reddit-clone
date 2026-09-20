package com.motadev.clone_reddit.user.convert;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.dtos.response.UserResponseDTO;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class UserConvert {
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public UserConvert(RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    public User convertDtoToEntity(UserRequestDTO dto) {
        var basicRole = roleRepository.findByName(RoleValues.BASIC.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));

        User user = new User();
        user.setUsername(dto.username());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRoles(Set.of(basicRole));

        return user;
    }

    public UserResponseDTO convertEntityToDTo(UUID userId) {
        Optional<User> dbUser = userRepository.findById(userId);
        if (dbUser.isEmpty()) {
            throw new ResourceNotFoundException("User not found.");
        }

        return new UserResponseDTO(
                dbUser.get().getUserId(),
                dbUser.get().getUsername(),
                dbUser.get().getEmail(),
                dbUser.get().getKarma()
        );
    }
}
