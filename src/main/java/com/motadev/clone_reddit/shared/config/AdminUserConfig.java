package com.motadev.clone_reddit.shared.config;

import com.motadev.clone_reddit.shared.logging.AdminEventLog;
import com.motadev.clone_reddit.user.entity.Role;
import com.motadev.clone_reddit.user.entity.User;
import com.motadev.clone_reddit.user.entity.enums.RoleValues;
import com.motadev.clone_reddit.user.repository.RoleRepository;
import com.motadev.clone_reddit.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class AdminUserConfig implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminEventLog adminEventLog;

    public AdminUserConfig(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           AdminEventLog adminEventLog) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEventLog = adminEventLog;
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
                user -> adminEventLog.seedSkipped("admin"),
                () -> {
                    var user = new User();
                    user.setUsername("admin");
                    user.setPassword(passwordEncoder.encode("123"));
                    user.setRoles(Set.of(roleAdmin));
                    userRepository.save(user);

                    adminEventLog.seedCreated("admin");
                }
        );
    }
}
