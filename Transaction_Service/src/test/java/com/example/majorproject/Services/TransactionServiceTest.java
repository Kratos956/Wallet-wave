package com.example.majorproject.Services;


import com.example.majorproject.Dtos.CreateTransactionDTO;
import com.example.majorproject.Exception.UserNotFoundException;
import com.example.majorproject.Models.Transaction;
import com.example.majorproject.Models.TransactionStatus;
import com.example.majorproject.Repositories.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.text.ParseException;

import static org.hamcrest.Matchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class TransactionServiceTest {

    @InjectMocks
    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    ObjectMapper objectMapper;

    @Before
    public void setUp() {
        transactionService.TRANSACTION_CREATED_TOPIC = "transaction-created";
        transactionService.NOTIFICATION_STATUS_TOPIC="notification-status";
    }

    @Test
    public void SuccessfulTransactionDone() throws JsonProcessingException {
        // Arrange
        CreateTransactionDTO createTransactionDTO = new CreateTransactionDTO();
        createTransactionDTO.setSender(1);
        createTransactionDTO.setReceiver(2);
        createTransactionDTO.setAmount(5L);
        createTransactionDTO.setComment("Transaction Dto");

        Transaction mockTxn = new Transaction();
        mockTxn.setStatus(TransactionStatus.PENDING);

        Mockito.when(transactionRepository.save(Mockito.any())).thenReturn(mockTxn);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any(Transaction.class))).thenReturn("Json-Object");
        Mockito.when(kafkaTemplate.send(transactionService.TRANSACTION_CREATED_TOPIC, "Json-Object"))
                .thenReturn(null); // we don't care about the future in this unit test

        // Act
        Transaction transaction = transactionService.send(createTransactionDTO);

        // Assert
        Assert.assertNotNull(transaction);
        Assert.assertEquals(TransactionStatus.PENDING, transaction.getStatus());

        // Verify interactions
        Mockito.verify(transactionRepository, Mockito.times(1)).save(Mockito.any());
        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any(Transaction.class));
        Mockito.verify(kafkaTemplate, Mockito.times(1))
                .send(transactionService.TRANSACTION_CREATED_TOPIC, "Json-Object");
    }



    @Test
    public void JsonParsingException() throws JsonProcessingException {
        // Arrange
        CreateTransactionDTO createTransactionDTO = new CreateTransactionDTO();
        createTransactionDTO.setSender(1);
        createTransactionDTO.setReceiver(2);
        createTransactionDTO.setAmount(5L);
        createTransactionDTO.setComment("Transaction Dto");

        Transaction mockTxn = new Transaction();
        mockTxn.setStatus(TransactionStatus.PENDING);

        Mockito.when(transactionRepository.save(Mockito.any())).thenReturn(mockTxn);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any(Transaction.class))).thenThrow(new JsonProcessingException("boom") {});

        // Act
        Transaction transaction = transactionService.send(createTransactionDTO);


        //Asserts
        Assert.assertNotNull(transaction);
        Assert.assertEquals(TransactionStatus.PENDING, transaction.getStatus());


        // Verify interactions
        Mockito.verify(transactionRepository, Mockito.times(1)).save(Mockito.any());
        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any(Transaction.class));
        Mockito.verify(kafkaTemplate, Mockito.never())
                .send(Mockito.anyString(), Mockito.anyString());

    }


    @Test
    public void updateTransaction_missingSenderOrReceiver_throwsException() throws JsonProcessingException {
        // Arrange
        String msg = "{"
                + "\"status\":\"success\","
                + "\"externalTransactionId\":\"txn-123\","
                // sender is missing here
                + "\"receiver\":\"user2\""
                + "}";

        Transaction mockTxn = new Transaction();
        mockTxn.setExternalTransactionId("txn-123");

        Mockito.when(transactionRepository.getByExternalTransactionId("txn-123"))
                .thenReturn(mockTxn);

        // Act + Assert
        Assert.assertThrows(UserNotFoundException.class, () -> transactionService.updateTransaction(msg));

        // Verify that txn was marked FAILED and saved
        Mockito.verify(transactionRepository, Mockito.times(1)).save(mockTxn);

        // Kafka should not be called because exception is thrown before notification publish
        Mockito.verifyNoInteractions(kafkaTemplate);
    }



    @Test
    public void createTransaction_nullSender_throwsUserNotFoundException() {
        // Arrange
        CreateTransactionDTO dto = new CreateTransactionDTO();
        dto.setSender(null);  // missing sender
        dto.setReceiver(2);
        dto.setAmount(50L);
        dto.setComment("Invalid Txn");

        // Act + Assert
        Assert.assertThrows(UserNotFoundException.class, () -> transactionService.send(dto));

        // Verify no DB or Kafka interaction
        Mockito.verifyNoInteractions(transactionRepository);
        Mockito.verifyNoInteractions(kafkaTemplate);
    }


    @Test
    public void updateTransactionSuccessfully() throws JsonProcessingException {
        // Arrange
        String msg = "{"
                + "\"status\":\"success\","
                + "\"externalTransactionId\":\"txn-123\","
                + "\"sender\":\"user1\","
                + "\"receiver\":\"user2\""
                + "}";

        Transaction mockTxn = new Transaction();
        mockTxn.setExternalTransactionId("txn-123");

        // Repo returns a transaction for this externalTransactionId
        Mockito.when(transactionRepository.getByExternalTransactionId("txn-123"))
                .thenReturn(mockTxn);

        // ObjectMapper serializes the notification payload
        Mockito.when(objectMapper.writeValueAsString(Mockito.any()))
                .thenReturn("notification-json");

        // Act
        transactionService.updateTransaction(msg);

        // Assert
        // 1. Transaction status updated to SUCCESS
        Mockito.verify(transactionRepository, Mockito.times(1))
                .updateTransaction("txn-123", TransactionStatus.SUCCESS);

        // 2. Notification sent to Kafka
        Mockito.verify(kafkaTemplate, Mockito.times(1))
                .send(eq(transactionService.NOTIFICATION_STATUS_TOPIC), eq("notification-json"));
    }



    @Test
    public void updateTransactionFailed() throws JsonProcessingException {
        // Arrange
        String msg = "{"
                + "\"status\":\"failed\","
                + "\"externalTransactionId\":\"txn-123\","
                + "\"sender\":\"user1\","
                + "\"receiver\":\"user2\""
                + "}";

        Transaction mockTxn = new Transaction();
        mockTxn.setExternalTransactionId("txn-123");

        // Repo returns a transaction for this externalTransactionId
        Mockito.when(transactionRepository.getByExternalTransactionId("txn-123"))
                .thenReturn(mockTxn);

        // ObjectMapper serializes the notification payload
        Mockito.when(objectMapper.writeValueAsString(Mockito.any()))
                .thenReturn("notification-json");

        // Act
        transactionService.updateTransaction(msg);

        // Assert
        // 1. Transaction status updated to FAILED
        Mockito.verify(transactionRepository, Mockito.times(1))
                .updateTransaction("txn-123", TransactionStatus.FAILED);

        // 2. Notification sent to Kafka
        Mockito.verify(kafkaTemplate, Mockito.times(1))
                .send(eq(transactionService.NOTIFICATION_STATUS_TOPIC), eq("notification-json"));
    }



    @Test
    public void updateTransaction_invalidJson() throws JsonProcessingException {
        // Arrange
        String msg = "invalid-json";

        // Act
        transactionService.updateTransaction(msg);

        // Assert
        Mockito.verify(transactionRepository, Mockito.never())
                .updateTransaction(Mockito.anyString(), Mockito.any());

        Mockito.verifyNoInteractions(kafkaTemplate);
    }



    @Test
    public void updateTransaction_missingTxnId() throws JsonProcessingException {
        // Arrange
        String msg = "{"
                + "\"status\":\"success\","
                + "\"sender\":\"user1\","
                + "\"receiver\":\"user2\""
                + "}"; // externalTransactionId missing

        Mockito.when(transactionRepository.getByExternalTransactionId(null))
                .thenReturn(null);

        // Act
        transactionService.updateTransaction(msg);

        // Assert
        // No transaction update since txn was null
        Mockito.verify(transactionRepository, Mockito.never())
                .updateTransaction(Mockito.anyString(), Mockito.any());

        // No Kafka publish
        Mockito.verifyNoInteractions(kafkaTemplate);
    }



    @Test
    public void updateTransaction_unexpectedStatus() throws JsonProcessingException {
        // Arrange
        String msg = "{"
                + "\"status\":\"random\","
                + "\"externalTransactionId\":\"txn-123\","
                + "\"sender\":\"user1\","
                + "\"receiver\":\"user2\""
                + "}";

        Transaction mockTxn = new Transaction();
        mockTxn.setExternalTransactionId("txn-123");

        Mockito.when(transactionRepository.getByExternalTransactionId("txn-123"))
                .thenReturn(mockTxn);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any()))
                .thenReturn("notification-json");

        // Act
        transactionService.updateTransaction(msg);

        // Assert
        // 1. Repo still updates to FAILED
        Mockito.verify(transactionRepository, Mockito.times(1))
                .updateTransaction("txn-123", TransactionStatus.FAILED);

        // 2. Notification still sent to Kafka
        Mockito.verify(kafkaTemplate, Mockito.times(1))
                .send(eq(transactionService.NOTIFICATION_STATUS_TOPIC), eq("notification-json"));
    }







}
