package com.example.majorproject.Services;


import com.example.majorproject.Dtos.CreateUserDto;


import com.example.majorproject.Dtos.UpdateUserDto;
import com.example.majorproject.Models.User;
import com.example.majorproject.Repositories.UserRepository;
import com.example.majorproject.Security.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;


@Service
public class UserService {

    @Value("${kafka.topic.user-created}")
    private String USER_CREATED_TOPIC;


    private final Logger logger = LoggerFactory.getLogger(UserService.class);


    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    UserCacheService userCacheService;

    @Autowired
    UserRepository userRepository;


    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;




    public User create(CreateUserDto createUserDto) {
            logger.info("Creating new user: {}", createUserDto.getName());
            User user=createUserDto.convertToUser();
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            userCacheService.saveUser(user);
            Integer userId = user.getId();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", userId);
            try {
                String data = objectMapper.writeValueAsString(jsonObject);
                logger.info("data to be published - {}", data);
                logger.info("Greeting notification send for UserId - {}", user.getId());
                this.kafkaTemplate.send(USER_CREATED_TOPIC, data);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize user for Kafka", e);
            }
            return user;
    }

        public User updateUser(Integer id, UpdateUserDto updateDto) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (updateDto.getName() != null) existingUser.setName(updateDto.getName());
        if (updateDto.getAge() != null) existingUser.setAge(updateDto.getAge());
        if (updateDto.getEmail() != null) existingUser.setEmail(updateDto.getEmail());
        if (updateDto.getPhone() != null) existingUser.setPhone(updateDto.getPhone());

        User updatedUser = userRepository.save(existingUser);
        userCacheService.saveUser(updatedUser);

        return updatedUser; // 👈 returned as JSON automatically
    }


    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + email));
    }

}
