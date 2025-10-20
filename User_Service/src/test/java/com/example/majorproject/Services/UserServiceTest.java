package com.example.majorproject.Services;

import com.example.majorproject.Dtos.CreateUserDto;
import com.example.majorproject.Dtos.UpdateUserDto;
import com.example.majorproject.Models.User;
import com.example.majorproject.Repositories.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.FactoryBasedNavigableListAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    UserCacheService userCacheService;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;


    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manually set the Kafka topic (because @Value won't inject in unit tests)
        ReflectionTestUtils.setField(userService, "USER_CREATED_TOPIC", "user-created");
    }

    @Test
    public void testCreateUser_Success() throws JsonProcessingException {
        CreateUserDto dto = new CreateUserDto("John", 25, "john@example.com", "1234567890", "password123");
        User mockUser=dto.convertToUser();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1); // simulate DB assigning ID
            return u;
        });
        when(objectMapper.writeValueAsString(any(JSONObject.class))).thenReturn("{\"id\":1}");

        User createdUser=userService.create(dto);


        // Assert
        // Assert
        Assert.assertNotNull(createdUser);
        Assert.assertEquals(1, createdUser.getId().intValue()); // ✅ FIXED
        Assert.assertEquals("encodedPassword", createdUser.getPassword());


        // Verify interactions
        verify(userRepository, times(1)).save(any(User.class));
        verify(userCacheService, times(1)).saveUser(any(User.class));
        verify(kafkaTemplate, times(1)).send(eq("user-created"), anyString());
    }


    @Test
    public void catchJsonParseException() throws JsonProcessingException {
        CreateUserDto dto = new CreateUserDto("John", 25, "john@example.com", "1234567890", "password123");
        User mockUser=dto.convertToUser();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1); // simulate DB assigning ID
            return u;
        });
        when(objectMapper.writeValueAsString(any(JSONObject.class))).thenThrow(JsonProcessingException.class);

        User createdUser=userService.create(dto);


        verify(userRepository, times(1)).save(any(User.class));
        verify(userCacheService, times(1)).saveUser(any(User.class));
    }


    @Test
    public void testUpdateUser_Success() {
        Integer userId = 1;
        User existingUser = new User();
        existingUser.setId(userId);
        existingUser.setName("Old Name");
        existingUser.setAge(20);
        existingUser.setEmail("old@example.com");
        existingUser.setPhone("1111111111");

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("New Name");
        updateDto.setAge(25);
        updateDto.setEmail("new@example.com");
        updateDto.setPhone("9999999999");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updatedUser = userService.updateUser(userId, updateDto);

        Assert.assertNotNull(updatedUser);
        Assert.assertEquals("New Name", updatedUser.getName());
        Assert.assertEquals(25, updatedUser.getAge().intValue());
        Assert.assertEquals("new@example.com", updatedUser.getEmail());
        Assert.assertEquals("9999999999", updatedUser.getPhone());


        verify(userRepository, times(1)).findById(userId);
        verify(userRepository, times(1)).save(existingUser);
        verify(userCacheService, times(1)).saveUser(existingUser);


    }


    @Test
    public void testUpdateUser_UserNotFound() {
        // Arrange
        Integer userId = 1;
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("New Name");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updateUser(userId, updateDto));

        Assert.assertEquals("User not found with id: " + userId, exception.getMessage());

        verify(userRepository, times(1)).findById(userId);
        verify(userRepository, never()).save(any(User.class));
        verify(userCacheService, never()).saveUser(any(User.class));
    }

    @Test
    public void find_By_Email_Success(){
        String UserEmail="sanit@gmail.com";

        User user=new User();
        user.setEmail(UserEmail);

        when(userRepository.findByEmail(UserEmail)).thenReturn(Optional.of(user));

        User foundUser=userService.findByEmail(UserEmail);

        Assert.assertNotNull(foundUser);
    }


    @Test
    public void testFindByEmail_UserNotFound() {
        // Arrange
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> userService.findByEmail(email)
        );

        Assert.assertEquals("User not found with email: " + email, exception.getMessage());
        verify(userRepository, times(1)).findByEmail(email);
    }




}

