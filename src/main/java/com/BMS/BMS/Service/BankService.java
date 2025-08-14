package com.BMS.BMS.Service;

import com.BMS.BMS.Models.*;
import com.BMS.BMS.Reppo.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankFundRepository bankFundRepository;
    private final MicroDepositRepository microDepositRepository;
    private final TransactionRepository transactionRepository;

    public BankService(CustomerRepository customerRepository,
                       BankFundRepository bankFundRepository,
                       MicroDepositRepository microDepositRepository,
                       TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.bankFundRepository = bankFundRepository;
        this.microDepositRepository = microDepositRepository;
        this.transactionRepository = transactionRepository;
    }

    public String sendMicroDeposit(String accountNumber) {
        Optional<Customer> customerOpt = customerRepository.findByAccountNumber(accountNumber);
        if (customerOpt.isEmpty()) return "Account not found";

        Customer customer = customerOpt.get();
        BigDecimal microDepositAmount = BigDecimal.valueOf(0.50);

        // Deduct from BankFund
        BankFund bankFund = getBankFund();
        if (bankFund.getFundForLoan().compareTo(microDepositAmount) < 0) {
            return "Insufficient bank funds for micro deposit";
        }
        bankFund.setFundForLoan(bankFund.getFundForLoan().subtract(microDepositAmount));
        bankFundRepository.save(bankFund);

        // Save micro deposit record
        MicroDeposit microDeposit = new MicroDeposit(microDepositAmount, "PENDING_VERIFICATION", customer);
        microDepositRepository.save(microDeposit);

        // Save transaction
        Transaction tx = new Transaction(microDepositAmount, "MICRO_DEPOSIT", LocalDateTime.now(), customer);
        transactionRepository.save(tx);

        return "Micro deposit of $" + microDepositAmount + " sent to account: " + accountNumber;
    }

    public String confirmMicroDeposit(String accountNumber, BigDecimal depositAmount) {
        Optional<Customer> customerOpt = customerRepository.findByAccountNumber(accountNumber);
        if (customerOpt.isEmpty()) return "Account not found";

        Customer customer = customerOpt.get();
        Optional<MicroDeposit> latestDepositOpt = microDepositRepository.findTopByCustomerOrderByIdDesc(customer);

        if (latestDepositOpt.isEmpty()) return "No micro deposit found.";
        MicroDeposit latestDeposit = latestDepositOpt.get();

        if (latestDeposit.getAmount().compareTo(depositAmount) == 0) {
            latestDeposit.setStatus("VERIFIED");
            microDepositRepository.save(latestDeposit);
            return "Account verified successfully.";
        }
        return "Micro deposit amount does not match.";
    }

    public String disburseLoan(String accountNumber, BigDecimal loanAmount) {
        Optional<Customer> customerOpt = customerRepository.findByAccountNumber(accountNumber);
        if (customerOpt.isEmpty()) return "Account not found";

        Customer customer = customerOpt.get();

        // ✅ Check if the microdeposit status is VERIFIED
        Optional<MicroDeposit> latestDepositOpt = microDepositRepository.findTopByCustomerOrderByIdDesc(customer);
        if (latestDepositOpt.isEmpty() || !"VERIFIED".equalsIgnoreCase(latestDepositOpt.get().getStatus())) {
            return "Account is not verified for loan disbursement.";
        }

        BankFund bankFund = getBankFund();

        if (bankFund.getFundForLoan().compareTo(loanAmount) < 0) {
            return "Insufficient bank funds";
        }

        // Deduct from BankFund
        bankFund.setFundForLoan(bankFund.getFundForLoan().subtract(loanAmount));
        bankFundRepository.save(bankFund);

        // Update customer loan details
        customer.setAmount(customer.getAmount().add(loanAmount));
        customer.setLoanRemaining(customer.getLoanRemaining().add(loanAmount));
        customer.setTotalLoan(customer.getTotalLoan().add(loanAmount));
        customerRepository.save(customer);

        // Save transaction
        Transaction tx = new Transaction(loanAmount, "LOAN_DISBURSE", LocalDateTime.now(), customer);
        transactionRepository.save(tx);

        return "Loan of $" + loanAmount + " disbursed to account: " + accountNumber;
    }


    public String repayLoan(String accountNumber, BigDecimal repaymentAmount) {
        Optional<Customer> customerOpt = customerRepository.findByAccountNumber(accountNumber);
        if (customerOpt.isEmpty()) return "Account not found";

        Customer customer = customerOpt.get();
        BankFund bankFund = getBankFund();

        // Add to BankFund
        bankFund.setFundForLoan(bankFund.getFundForLoan().add(repaymentAmount));
        bankFundRepository.save(bankFund);

        // Update customer repayment details
        customer.setLoanPaid(customer.getLoanPaid().add(repaymentAmount));
        customer.setLoanRemaining(customer.getLoanRemaining().subtract(repaymentAmount));
        customer.setAmount(customer.getAmount().subtract(repaymentAmount));
        customerRepository.save(customer);

        // Save transaction
        Transaction tx = new Transaction(repaymentAmount, "LOAN_REPAYMENT", LocalDateTime.now(), customer);
        transactionRepository.save(tx);

        return "Repayment of $" + repaymentAmount + " received from account: " + accountNumber;
    }

    private BankFund getBankFund() {
        return bankFundRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Bank fund record missing"));
    }
}

