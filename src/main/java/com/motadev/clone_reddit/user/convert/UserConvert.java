package com.motadev.clone_reddit.user.convert;

import com.motadev.clone_reddit.shared.exception.ResourceNotFoundException;
import com.motadev.clone_reddit.user.dtos.request.UserRequestDTO;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserConvert {
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserConvert(RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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
}
