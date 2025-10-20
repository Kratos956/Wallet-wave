package com.example.majorproject.Services;

import com.example.majorproject.Dtos.CreateTransactionDTO;
import com.example.majorproject.Exception.UserNotFoundException;
import com.example.majorproject.Models.Transaction;
import com.example.majorproject.Models.TransactionStatus;
import com.example.majorproject.Repositories.TransactionRepository;
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
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    @Value("${kafka.topic.transaction-created}")
    String TRANSACTION_CREATED_TOPIC;

    @Value("${kafka.topic.notification-status}")
    String NOTIFICATION_STATUS_TOPIC;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private final JSONParser jsonParser=new JSONParser();
    public Transaction send(CreateTransactionDTO createTransactionDTO)  {
        Transaction transaction = createTransactionDTO.convertToTransaction();

        if (transaction.getSender() == null || transaction.getReceiver() == null) {
            throw new UserNotFoundException("Sender or Receiver cannot be null");
        }

        transaction.setStatus(TransactionStatus.PENDING);
        transaction=transactionRepository.save(transaction);
        try{
            String data=objectMapper.writeValueAsString(transaction);
            logger.info("Publishing transaction-created event for txnId={}", transaction.getExternalTransactionId());
            kafkaTemplate.send(TRANSACTION_CREATED_TOPIC,data);
        }
        catch(JsonProcessingException e){
            logger.error("Failed to publish transaction-created event for txnId={}", transaction.getExternalTransactionId(), e);
        }

        return transaction;
    }

    @KafkaListener(topics = "${kafka.topic.wallet-updated}",groupId = "${spring.kafka.wallet-updated-group}")
    public void updateTransaction(String msg) throws JsonProcessingException {
        logger.info("Received wallet-updated event: {}", msg);
        JSONObject data;
        try {
            data = (JSONObject) jsonParser.parse(msg);
        } catch (ParseException e) {
            logger.error("Failed to parse wallet-updated event: {}", msg, e);
            return;
        }

        String walletUpdateStatus = (String) data.get("status");
        String externalTransactionId = (String) data.get("externalTransactionId");

        Transaction txn = transactionRepository.getByExternalTransactionId(externalTransactionId);

        if (txn == null) {
            logger.error("No transaction found for externalTransactionId={}", externalTransactionId);
            return;
        }
        if (data.get("sender") == null || data.get("receiver") == null) {
            // Option A: mark FAILED
            txn.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(txn);

            // Option B: delete it completely
            // transactionRepository.delete(transaction);

            throw new UserNotFoundException("Sender or receiver does not exist for txnId=" + externalTransactionId);
        }

        TransactionStatus transactionStatus = walletUpdateStatus.equals("success") ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
        this.transactionRepository.updateTransaction(externalTransactionId, transactionStatus);
        logger.info("Transaction status updated to" + transactionStatus);

        //Created a notification service and get notified if transaction is done or not

//        Transaction transaction=transactionRepository.getByExternalTransactionId(externalTransactionId);
        String notification = objectMapper.writeValueAsString(data);
        kafkaTemplate.send(NOTIFICATION_STATUS_TOPIC,notification);
        logger.info("Published notification-status event for txnId={}, status={}", externalTransactionId, transactionStatus);
    }
}
