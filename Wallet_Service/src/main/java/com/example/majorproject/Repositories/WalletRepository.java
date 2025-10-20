package com.example.majorproject.Repositories;

import com.example.majorproject.Models.Wallet;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet,Long> {
    Wallet findByUserId(Integer userId);

    @Transactional
    @Modifying
    @Query("update Wallet w set w.balance = w.balance + :amount where w.id = :id")
    void updateWallet(@Param("id") Integer id, @Param("amount") Long amount);

    @Query("SELECT w.balance FROM Wallet w WHERE w.userId = :userId")
    Long getBalanceByUserId(@Param("userId") Integer userId);

}
