package com.motadev.clone_reddit.shared.config;

import com.motadev.clone_reddit.auth.entity.Role;
import com.motadev.clone_reddit.auth.entity.User;
import com.motadev.clone_reddit.auth.entity.enums.RoleValues;
import com.motadev.clone_reddit.auth.repository.RoleRepository;
import com.motadev.clone_reddit.auth.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
@Slf4j
public class AdminUserConfig implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserConfig(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        var roleAdmin = roleRepository.findByName(RoleValues.ADMIN.name());

        if (roleAdmin == null) {
            var newRole = new Role();
            newRole.setName(RoleValues.ADMIN.name());
            roleRepository.save(newRole);
        }

        var userAdmin = userRepository.findByUsername("admin");

        userAdmin.ifPresentOrElse(
                user -> {
                    System.out.println("admin ja existe");
                },
                () -> {
                    var user = new User();
                    user.setUsername("admin");
                    user.setPassword(passwordEncoder.encode("123"));
                    user.setRoles(Set.of(roleAdmin));
                    userRepository.save(user);
                }
        );
    }
}
