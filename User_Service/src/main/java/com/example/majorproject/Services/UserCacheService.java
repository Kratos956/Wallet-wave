package com.example.majorproject.Services;

import com.example.majorproject.Models.User;
import com.example.majorproject.Repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserCacheService {

    private static Logger logger = LoggerFactory.getLogger(UserCacheService.class);

    @Autowired
    private UserRepository userRepository;

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String USER_KEY_PREFIX = "USER:";

    public UserCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ✅ Save user as Hash + reverse index
    public void saveUser(User user) {
        String key = USER_KEY_PREFIX + user.getId();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("phone", user.getPhone());

        // Save full hash
        redisTemplate.opsForHash().putAll(key, userMap);

        logger.info("🔄 Stored USER:{} in Redis as Hash + email index", user.getId());
    }


    // ✅ Fetch user from Redis Hash (fallback to DB if not found)
    public User getUserById(Integer id) {
        String key = USER_KEY_PREFIX + id;

        // 1️⃣ Try Redis Hash
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        if (!userMap.isEmpty()) {
            logger.info("✅ Cache hit for USER:{}", id);
            return mapToUser(userMap);
        }

        // 2️⃣ Fallback to DB
        logger.info("❌ Cache miss for USER:{}, fetching from DB", id);
        User user = userRepository.findById(id).orElse(null);

        if (user != null) {
            saveUser(user);
            logger.info("🔄 Stored USER:{} in Redis after DB fetch", id);
        }

        return user;
    }

    // ✅ Convenience method to fetch without DB fallback
    public User getUserFromCache(Integer userId) {
        String key = USER_KEY_PREFIX + userId;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        return userMap.isEmpty() ? null : mapToUser(userMap);
    }

    // 🔧 Helper: Convert Map → User
    private User mapToUser(Map<Object, Object> map) {
        User user = new User();
        user.setId((Integer) map.get("id"));
        user.setName((String) map.get("name"));
        user.setEmail((String) map.get("email"));
        user.setPhone((String) map.get("phone"));
        return user;
    }
}

