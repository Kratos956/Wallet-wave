package com.example.majorproject.Services;

import com.example.majorproject.Exceptions.WalletNotFoundException;
import com.example.majorproject.Models.CurrencyType;
import com.example.majorproject.Repositories.WalletRepository;
import com.example.majorproject.Models.Wallet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    @Value("${wallet.opening-balance}")
    private Long walletOpeningBalance;

//    private static final String USER_CREATED_TOPIC = "user-created";
//    private static final String TRANSACTION_CREATED_TOPIC="transaction-created";

    @Value("${kafka.topics.wallet-updated}")
    private String WALLET_UPDATED_TOPIC;
//    private static final String LOW_WALLET_BALANCE_TOPIC = "low-wallet-balance";

    private static final String currency="USD";

    private final JSONParser jsonParser=new JSONParser();

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    WalletCacheService walletCacheService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topic.user-created}",groupId = "${spring.kafka.user-created-group}")
    public void userCreated(String msg) {
        JSONObject data;
        try {
            data = (JSONObject) jsonParser.parse(msg);
        } catch (ParseException e) {
            logger.error("Failed to parse user-created event: {}", msg, e);
            return;
        }
        String id=data.get("id").toString();
        Integer userid=Integer.parseInt(id);

        Wallet wallet=walletRepository.findByUserId(userid);


        if (wallet!=null){
            logger.warn("Wallet already exists for {}", userid);
            return;
        }
        wallet=Wallet.builder().userId(userid).balance(walletOpeningBalance).currency(CurrencyType.USD).build();
        walletRepository.save(wallet);
        walletCacheService.saveWallet(wallet);
        logger.info("Wallet created for {}", userid);
    }

    @KafkaListener(topics = "${kafka.topics.transaction-created}", groupId = "${spring.kafka.transaction-created-group}")
    public void transactionCreated(String msg) throws JsonProcessingException {
        JSONObject data;
        try {
            data = (JSONObject) jsonParser.parse(msg);
        } catch (ParseException e) {
            logger.error("Failed to parse transaction-created event: {}", msg, e);
            return;
        }

        logger.info("Received transaction-created event: {}", msg);

        Integer sender = ((Number) data.get("sender")).intValue();
        Integer receiver = ((Number) data.get("receiver")).intValue();
        Long amount = ((Number) data.get("amount")).longValue();
        String externalTransactionId = (String) data.get("externalTransactionId");

        // ✅ Use cache (with fallback to DB)
        Wallet senderWallet = walletCacheService.getWallet(sender);
        Wallet receiverWallet = walletCacheService.getWallet(receiver);

        // Validation
        if (senderWallet == null || senderWallet.getBalance() == null) {
            logger.warn("Sender wallet not found for {}", sender);
            JSONObject walletEventData = FailedEvent(sender, receiver, amount, externalTransactionId, "Sender wallet not found");
            kafkaTemplate.send(WALLET_UPDATED_TOPIC, objectMapper.writeValueAsString(walletEventData));
            return;
        }
        if (receiverWallet == null) {
            logger.warn("Receiver wallet not found for {}", receiver);
            JSONObject walletEventData = FailedEvent(sender, receiver, amount, externalTransactionId, "Receiver wallet not found");
            kafkaTemplate.send(WALLET_UPDATED_TOPIC, objectMapper.writeValueAsString(walletEventData));
            return;
        }
        if (senderWallet.getBalance() < amount) {
            logger.warn("Insufficient balance for {}", sender);
            JSONObject walletEventData = FailedEvent(sender, receiver, amount, externalTransactionId, "Insufficient balance");
            kafkaTemplate.send(WALLET_UPDATED_TOPIC, objectMapper.writeValueAsString(walletEventData));
            return;
        }

        // ✅ Update DB
        walletRepository.updateWallet(senderWallet.getId(), -amount);
        walletRepository.updateWallet(receiverWallet.getId(), amount);

        // ✅ Refresh from DB and update Redis (ensure sync)
        Wallet senderWalletUpdated = walletRepository.findByUserId(sender);
        Wallet receiverWalletUpdated = walletRepository.findByUserId(receiver);

        walletCacheService.saveWallet(senderWalletUpdated);
        walletCacheService.saveWallet(receiverWalletUpdated);

        logger.info("Wallets updated: senderId={}, receiverId={}, amount={}", sender, receiver, amount);

        // ✅ Send event
        JSONObject walletEventData = SuccessEvent(sender, receiver, amount, externalTransactionId,
                senderWalletUpdated.getId(), receiverWalletUpdated.getId());
        kafkaTemplate.send(WALLET_UPDATED_TOPIC, objectMapper.writeValueAsString(walletEventData));

        logger.info("Published wallet-updated event for txnId={} with status={}", externalTransactionId, walletEventData.get("status"));
    }

    public JSONObject FailedEvent(Integer sender, Integer receiver, Long amount, String externalTransactionId, String reason) {
        JSONObject data = new JSONObject();
        data.put("sender", sender);
        data.put("receiver", receiver);
        data.put("amount", amount);
        data.put("externalTransactionId", externalTransactionId);
        data.put("status", "failed");
        data.put("reason", reason);
        return data;
    }
    public JSONObject SuccessEvent(Integer sender,Integer receiver,Long amount,String externalTransactionId,Integer senderWalletId, Integer receiverWalletId){
        JSONObject data=new JSONObject();
        data.put("sender",sender);
        data.put("receiver",receiver);
        data.put("amount",amount);
        data.put("externalTransactionId",externalTransactionId);
        data.put("status","success");
        data.put("senderWalletId",senderWalletId);
        data.put("receiverWalletId",receiverWalletId);
        return data;
    }


    public Long getBalance(Integer userId) {
        Wallet wallet = walletCacheService.getWallet(userId);
        if (wallet == null) {
            throw new WalletNotFoundException("Wallet not found for userId " + userId);
        }
        return wallet.getBalance();
    }


}
