/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.entity
 * - File      : PaymentRequest.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : A record that a customer was asked to pay, and how
 * </pre>
 */
package com.ntaganira.heritier.ibook.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A record that a customer was asked to pay an invoice, and the instructions they were given.
 *
 * <p>This is <strong>not</strong> a payment link. A payment link is issued by a gateway, collects
 * money and reports back; none of that exists here, and inventing a URL that looked like one would be
 * the worst thing this page could do — somebody would send it to a customer.
 *
 * <p>What it is instead is the thing the gateway was only ever a convenience for: the payment
 * instructions, written once, recorded against the invoice, and sent by whoever is chasing it. In
 * Rwanda that is usually a MoMo code or bank details passed on by message anyway. Recording that the
 * request went out, when, for how much and through which channel is ordinary receivables work and
 * needs no gateway at all.
 *
 * <p><strong>No settlement state is stored.</strong> Whether the invoice has been paid is read off the
 * invoice, which is the only place that can answer it without the two disagreeing. A {@code PAID} flag
 * here would be a second version of the truth, and the day it drifted from the ledger it would be the
 * one somebody believed.
 */
@Entity
@Table(name = "payment_requests")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, unique = true)
    private String requestNo;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** Copied on, so a list of requests does not need one query per row to show what it is for. */
    @Column(name = "invoice_no", nullable = false)
    private String invoiceNo;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name")
    private String customerName;

    /**
     * What was asked for, which is not necessarily what the invoice is worth. A customer asked for a
     * part payment was asked for this figure, and the record should say what was actually asked.
     */
    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "RWF";

    /** How the customer was told to pay: MoMo, Airtel Money, bank transfer, cash. */
    @Column(name = "channel", nullable = false)
    private String channel;

    /** Where the money was to be sent — a MoMo code, an account number. Never a credential. */
    @Column(name = "pay_to")
    private String payTo;

    /** What the customer quotes so the receipt can be matched to the invoice. */
    @Column(name = "reference")
    private String reference;

    /** The message that was sent, kept as sent rather than rebuilt later from settings that changed. */
    @Column(name = "instructions", length = 1000)
    private String instructions;

    @Column(name = "requested_on", nullable = false)
    private LocalDate requestedOn;

    @Column(name = "cancelled", nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
