package com.erp.aierpbackend.config;

import com.erp.aierpbackend.entity.Role;
import com.erp.aierpbackend.entity.User;
import com.erp.aierpbackend.repository.RoleRepository;
import com.erp.aierpbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Initialize roles if they don't exist
        initRoles();
        
        // Create default admin user if it doesn't exist
        createDefaultAdminIfNotExists();
        
        // Create default verification manager if it doesn't exist
        createDefaultManagerIfNotExists();
    }
    
    private void initRoles() {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(Role.ERole.ROLE_USER));
            roleRepository.save(new Role(Role.ERole.ROLE_ADMIN));
            roleRepository.save(new Role(Role.ERole.ROLE_VERIFICATION_MANAGER));
        }
    }
    
    private void createDefaultAdminIfNotExists() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User(
                "admin",
                "admin@example.com",
                passwordEncoder.encode("#admin001")
            );
            
            Set<Role> roles = new HashSet<>();
            Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Error: Admin Role not found."));
            roles.add(adminRole);
            
            admin.setRoles(roles);
            userRepository.save(admin);
        }
    }
    
    private void createDefaultManagerIfNotExists() {
        if (!userRepository.existsByUsername("manager")) {
            User manager = new User(
                "manager",
                "manager@example.com",
                passwordEncoder.encode("#manager1")
            );
            
            Set<Role> roles = new HashSet<>();
            Role managerRole = roleRepository.findByName(Role.ERole.ROLE_VERIFICATION_MANAGER)
                    .orElseThrow(() -> new RuntimeException("Error: Verification Manager Role not found."));
            roles.add(managerRole);
            
            manager.setRoles(roles);
            userRepository.save(manager);
        }
    }
}
