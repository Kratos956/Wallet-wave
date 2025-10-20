package com.example.majorproject.Services;

import com.example.majorproject.Models.CurrencyType;
import com.example.majorproject.Models.Wallet;
import com.example.majorproject.Repositories.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


@Service
public class WalletCacheService {

    @Autowired
    private WalletRepository walletRepository;

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String WALLET_KEY_PREFIX = "WALLET:";

    public WalletCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ✅ Save wallet as Hash
    public void saveWallet(Wallet wallet) {
        String key = WALLET_KEY_PREFIX + wallet.getUserId();
        redisTemplate.opsForHash().put(key, "id", wallet.getId());
        redisTemplate.opsForHash().put(key, "userId", wallet.getUserId());
        redisTemplate.opsForHash().put(key, "balance", wallet.getBalance());
        redisTemplate.opsForHash().put(key, "currency", wallet.getCurrency().toString());
    }

    // ✅ Get Wallet from cache (no DB fallback)
    public Wallet getWalletFromCache(Integer userId) {
        String key = WALLET_KEY_PREFIX + userId;
        var walletMap = redisTemplate.opsForHash().entries(key);

        if (walletMap.isEmpty()) return null;

        Wallet wallet = new Wallet();
        wallet.setId((Integer) walletMap.get("id"));
        wallet.setUserId((Integer) walletMap.get("userId"));
        wallet.setBalance(((Number) walletMap.get("balance")).longValue());
        wallet.setCurrency(CurrencyType.valueOf((String) walletMap.get("currency")));


        return wallet;
    }

    // ✅ Get Wallet with DB fallback
    public Wallet getWallet(Integer userId) {
        Wallet wallet = getWalletFromCache(userId);

        if (wallet != null) {
            System.out.println("✅ Cache hit for WALLET:" + userId);
            return wallet;
        }

        System.out.println("❌ Cache miss for WALLET:" + userId + ", loading from DB...");
        wallet = walletRepository.findByUserId(userId);

        if (wallet != null) {
            saveWallet(wallet);
        }

        return wallet;
    }
}


