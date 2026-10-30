package com.dechouriog.ledger.account;

import com.dechouriog.ledger.common.ApiException;
import com.dechouriog.ledger.ledger.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accounts;
    private final LedgerService ledger;

    public AccountController(AccountRepository accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217, por ejemplo COP o USD") String currency) {
    }

    public record AccountResponse(UUID id, String name, String currency) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getCurrency());
        }
    }

    public record BalanceResponse(UUID accountId, long balanceMinor, String currency) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest req) {
        return AccountResponse.from(accounts.save(new Account(req.name(), req.currency())));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accounts.findById(id).orElseThrow(() -> ApiException.notFound("La cuenta")));
    }

    /** El saldo no se guarda: siempre se calcula sumando los asientos del ledger. */
    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        Account a = accounts.findById(id).orElseThrow(() -> ApiException.notFound("La cuenta"));
        return new BalanceResponse(a.getId(), ledger.balanceOf(a.getId()), a.getCurrency());
    }
}
