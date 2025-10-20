package com.example.majorproject.Services;


import com.example.majorproject.Exceptions.WalletNotFoundException;
import com.example.majorproject.Models.Wallet;
import com.example.majorproject.Repositories.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WalletServiceTest{

    @InjectMocks
    private WalletService walletService;

    @Mock
    private  KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private  WalletRepository walletRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WalletCacheService walletCacheService;


    @Before
    public void setUp() {
        // Ensure walletOpeningBalance is not null for tests
        ReflectionTestUtils.setField(walletService, "walletOpeningBalance", 10L);
        ReflectionTestUtils.setField(walletService, "WALLET_UPDATED_TOPIC", "wallet-updated-topic");
    }


    @Test
    public void UserCreatedTest(){
        String msg = "{ \"id\": 1, " +
                "\"name\": \"Alice\", " +
                "\"age\": 25, " +
                "\"email\": \"alice@example.com\", " +
                "\"phone\": \"9876543210\" }";

        when(walletRepository.findByUserId(anyInt())).thenReturn(null);


        walletService.userCreated(msg);

        verify(walletRepository).save(Mockito.argThat(wallet ->
                wallet.getUserId() == 1 &&
                        wallet.getBalance().equals(10L) &&
                        wallet.getCurrency().name().equals("USD")
        ));

    }


    @Test
    public void WalletAlreadyExists(){
        String msg = "{ \"id\": 1, " +
                "\"name\": \"Alice\", " +
                "\"age\": 25, " +
                "\"email\": \"alice@example.com\", " +
                "\"phone\": \"9876543210\" }";

        Wallet wallet = new Wallet();
        wallet.setUserId(1);

        when(walletRepository.findByUserId(anyInt())).thenReturn(wallet);

        walletService.userCreated(msg);

        verify(walletRepository, Mockito.times(1)).findByUserId(1);
        // Verify save never called
        verify(walletRepository, never()).save(any());
        // No Kafka calls
        Mockito.verifyNoInteractions(kafkaTemplate);

    }


    @Test
    public void InvalidJson(){
        String msg="Invalid-Json format";

        walletService.userCreated(msg);


        verify(walletRepository, never()).findByUserId(anyInt());
        verify(walletRepository, never()).save(any());
        // No Kafka interactions
        Mockito.verifyNoInteractions(kafkaTemplate);

    }

    @Test
    public void TransactionCreatedSuccessfully() throws Exception {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 5, " +
                "\"externalTransactionId\": \"txn-12345\" }";

        // Mock sender wallet
        Wallet senderWallet = new Wallet();
        senderWallet.setId(100); // mock DB id
        senderWallet.setUserId(1);
        senderWallet.setBalance(10L);

        // Mock receiver wallet
        Wallet receiverWallet = new Wallet();
        receiverWallet.setId(200); // mock DB id
        receiverWallet.setUserId(2);
        receiverWallet.setBalance(10L);

        // Updated wallets after DB update
        Wallet updatedSenderWallet = new Wallet();
        updatedSenderWallet.setId(100);
        updatedSenderWallet.setUserId(1);
        updatedSenderWallet.setBalance(5L); // deducted

        Wallet updatedReceiverWallet = new Wallet();
        updatedReceiverWallet.setId(200);
        updatedReceiverWallet.setUserId(2);
        updatedReceiverWallet.setBalance(15L); // added

        // Mock cache lookup
        when(walletCacheService.getWallet(1)).thenReturn(senderWallet);
        when(walletCacheService.getWallet(2)).thenReturn(receiverWallet);

        // Mock DB update
        doNothing().when(walletRepository).updateWallet(eq(100), eq(-5L));
        doNothing().when(walletRepository).updateWallet(eq(200), eq(5L));

        // Mock DB fetch after update
        when(walletRepository.findByUserId(1)).thenReturn(updatedSenderWallet);
        when(walletRepository.findByUserId(2)).thenReturn(updatedReceiverWallet);

        // Stub ObjectMapper serialization
        when(objectMapper.writeValueAsString(any())).thenReturn("success-json");

        // Act
        walletService.transactionCreated(msg);

        // Assert/Verify
        verify(walletCacheService, times(1)).getWallet(1);
        verify(walletCacheService, times(1)).getWallet(2);

        verify(walletRepository, times(1)).updateWallet(100, -5L);
        verify(walletRepository, times(1)).updateWallet(200, 5L);

        verify(walletRepository, times(1)).findByUserId(1);
        verify(walletRepository, times(1)).findByUserId(2);

        verify(walletCacheService, times(1)).saveWallet(updatedSenderWallet);
        verify(walletCacheService, times(1)).saveWallet(updatedReceiverWallet);

        verify(kafkaTemplate, times(1)).send(eq("wallet-updated-topic"), eq("success-json"));

    }



    @Test
    public void TransactionCreated_SenderWalletNotFound() throws Exception {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 5, " +
                "\"externalTransactionId\": \"txn-12345\" }";

        // Sender wallet missing
        when(walletCacheService.getWallet(any())).thenReturn(null);

        when(objectMapper.writeValueAsString(any())).thenReturn("failed-json");

        walletService.transactionCreated(msg);

        verify(walletRepository, never()).updateWallet(anyInt(), anyLong());
        verify(kafkaTemplate).send("wallet-updated-topic", "failed-json");
    }


    @Test
    public void TransactionCreated_ReceiverWalletNotFound() throws Exception {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 5, " +
                "\"externalTransactionId\": \"txn-12345\" }";

        Wallet senderWallet = new Wallet();
        senderWallet.setId(100);
        senderWallet.setUserId(1);
        senderWallet.setBalance(20L);



        Mockito.when(walletCacheService.getWallet(1)).thenReturn(senderWallet);
        Mockito.when(walletCacheService.getWallet(2)).thenReturn(null);

        Mockito.when(objectMapper.writeValueAsString(any())).thenReturn("failed-json");

        walletService.transactionCreated(msg);

        verify(walletRepository, never()).updateWallet(anyInt(), anyLong());
        verify(kafkaTemplate).send("wallet-updated-topic", "failed-json");
    }



    @Test
    public void TransactionCreated_InsufficientBalance() throws Exception {
        String msg = "{ \"sender\": 1, " +
                "\"receiver\": 2, " +
                "\"amount\": 50, " +
                "\"externalTransactionId\": \"txn-12345\" }";

        Wallet senderWallet = new Wallet();
        senderWallet.setId(100);
        senderWallet.setUserId(1);
        senderWallet.setBalance(10L); // less than amount

        Wallet receiverWallet = new Wallet();
        receiverWallet.setId(200);
        receiverWallet.setUserId(2);
        receiverWallet.setBalance(20L);

        Mockito.when(walletCacheService.getWallet(1)).thenReturn(senderWallet);
        Mockito.when(walletCacheService.getWallet(2)).thenReturn(receiverWallet);

        when(objectMapper.writeValueAsString(any())).thenReturn("failed-json");

        walletService.transactionCreated(msg);

        verify(walletRepository, never()).updateWallet(anyInt(), anyLong());
        verify(kafkaTemplate).send("wallet-updated-topic", "failed-json");
    }


    @Test
    public void TransactionCreated_InvalidJson() throws Exception {
        String msg = "invalid-json";

        walletService.transactionCreated(msg);

        verify(walletRepository, never()).findByUserId(anyInt());
        verify(walletRepository, never()).updateWallet(anyInt(), anyLong());
        Mockito.verifyNoInteractions(kafkaTemplate);
    }


    @Test
    public void get_balance_success()  {
        Wallet wallet = new Wallet();
        Integer userId = 1;
        Long balance = 100L;
        wallet.setUserId(userId);
        wallet.setBalance(balance);

        Mockito.when(walletCacheService.getWallet(userId)).thenReturn(wallet);

        walletService.getBalance(userId);

    }

    @Test
    public void get_balance_Failed() throws Exception  {


        Mockito.when(walletCacheService.getWallet(anyInt())).thenReturn(null);

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.getBalance(anyInt()));

    }




}