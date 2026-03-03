package com.campuseventhub.config;

import com.campuseventhub.category.entity.Category;
import com.campuseventhub.category.repository.CategoryRepository;
import com.campuseventhub.event.entity.Event;
import com.campuseventhub.event.entity.EventStatus;
import com.campuseventhub.event.repository.EventRepository;
import com.campuseventhub.user.entity.Role;
import com.campuseventhub.user.entity.User;
import com.campuseventhub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      EventRepository eventRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User admin = userRepository.findByUsername("admin")
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername("admin");
                    user.setEmail("admin@campus.local");
                    user.setPasswordHash(passwordEncoder.encode("Admin@123"));
                    user.setRole(Role.ADMIN);
                    user.setEnabled(true);
                    return userRepository.save(user);
                });

        User student = userRepository.findByUsername("student1")
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername("student1");
                    user.setEmail("student1@campus.local");
                    user.setPasswordHash(passwordEncoder.encode("Student@123"));
                    user.setRole(Role.USER);
                    user.setEnabled(true);
                    return userRepository.save(user);
                });

        Category tech = categoryRepository.findByNameIgnoreCase("Tech Talks")
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName("Tech Talks");
                    category.setDescription("Engineering and software sessions");
                    return categoryRepository.save(category);
                });

        Category clubs = categoryRepository.findByNameIgnoreCase("Clubs")
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName("Clubs");
                    category.setDescription("Student club events and meetups");
                    return categoryRepository.save(category);
                });

        if (eventRepository.count() == 0) {
            Event sample = new Event();
            sample.setTitle("Spring Boot Career Workshop");
            sample.setDescription("Hands-on backend workshop with architecture and testing guidance");
            sample.setLocation("Engineering Hall - Room 204");
            sample.setStartTime(OffsetDateTime.now().plusDays(7).withHour(16).withMinute(0).withSecond(0).withNano(0));
            sample.setEndTime(OffsetDateTime.now().plusDays(7).withHour(18).withMinute(0).withSecond(0).withNano(0));
            sample.setCapacity(120);
            sample.setStatus(EventStatus.APPROVED);
            sample.setSubmittedBy(student);
            sample.setReviewedBy(admin);
            sample.setReviewedAt(OffsetDateTime.now());
            sample.setReviewComment("Great event");
            sample.setCategories(Set.of(tech, clubs));
            eventRepository.save(sample);
            log.info("Seeded initial sample event");
        }

        log.info("Seed data ready. admin/admin credentials created for local development");
    }
}
