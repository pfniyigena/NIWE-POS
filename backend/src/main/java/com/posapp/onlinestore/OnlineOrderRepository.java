package com.posapp.onlinestore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {
    Optional<OnlineOrder> findByCode(String code);

    /** Orders that hold a use of a coupon: everything except rejected and cancelled ones. */
    long countByCouponIdAndStatusNotIn(Long couponId, java.util.Collection<String> statuses);

    long countByCouponIdAndCouponKeyAndStatusNotIn(Long couponId, String couponKey, java.util.Collection<String> statuses);

    long countByCouponIdAndShopCustomerIdAndStatusNotIn(Long couponId, Long shopCustomerId, java.util.Collection<String> statuses);

    List<OnlineOrder> findAllByOrderByCreatedAtDesc();

    List<OnlineOrder> findByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    long countByStatusAndCreatedAtBefore(String status, Instant before);

    /** Orders still waiting for an answer that the shop has not been told about yet. */
    List<OnlineOrder> findByStatusAndAlertedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant before);

    List<OnlineOrder> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant before);

    long countByPaymentStatus(String paymentStatus);

    List<OnlineOrder> findTop50ByShopCustomerIdOrderByCreatedAtDesc(Long shopCustomerId);

    /** A customer who proved an e-mail address gets the orders earlier placed as a guest with it. Orders already in an account are never moved. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update OnlineOrder o set o.shopCustomerId = :id where o.shopCustomerId is null and lower(o.customerEmail) = :email")
    int linkGuestOrders(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("email") String email);

    /** The same for orders placed with the phone number that was just proved by a text message. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update OnlineOrder o set o.shopCustomerId = :id where o.shopCustomerId is null and o.phoneKey = :key")
    int linkGuestOrdersByPhone(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("key") String key);

    /** The account is deleted: its orders stay (the shop needs them for its books) but no longer belong to it. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update OnlineOrder o set o.shopCustomerId = null where o.shopCustomerId = :id")
    int unlinkOrders(@org.springframework.data.repository.query.Param("id") Long id);

    long countByCustomerPhoneAndStatus(String customerPhone, String status);

    long countByCreatedAtAfter(Instant since);
}
