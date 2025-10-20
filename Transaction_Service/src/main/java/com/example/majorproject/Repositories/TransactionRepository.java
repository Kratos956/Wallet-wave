package com.example.majorproject.Repositories;

import com.example.majorproject.Models.Transaction;
import com.example.majorproject.Models.TransactionStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Transaction t set t.status = ?2 where t.externalTransactionId = ?1")
    void updateTransaction(String externalTransactionId, TransactionStatus status);

    Transaction getByExternalTransactionId(String externalTransactionId);
}
