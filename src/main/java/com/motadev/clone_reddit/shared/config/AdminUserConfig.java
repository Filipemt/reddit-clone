package com.motadev.clone_reddit.shared.config;

import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
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

        Role roleAdmin = roleRepository.findByName(RoleValues.ADMIN.name())
                .orElseGet(() -> {
                    var newRole = new Role();
                    newRole.setName(RoleValues.ADMIN.name());
                    return roleRepository.save(newRole);
                });

        var userAdmin = userRepository.findByUsernameAndIsActiveTrue("admin");

        userAdmin.ifPresentOrElse(
                user -> log.atInfo()
                        .addKeyValue("event", "admin.seed.skipped")
                        .addKeyValue("username", "admin")
                        .setMessage("Admin user already exists")
                        .log(),
                () -> {
                    var user = new User();
                    user.setUsername("admin");
                    user.setPassword(passwordEncoder.encode("123"));
                    user.setRoles(Set.of(roleAdmin));
                    userRepository.save(user);

                    log.atInfo()
                            .addKeyValue("event", "admin.seed.created")
                            .addKeyValue("username", "admin")
                            .setMessage("Admin user seeded")
                            .log();
                }
        );
    }
}
