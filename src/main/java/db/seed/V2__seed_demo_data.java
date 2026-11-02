package db.seed;

import com.loanledger.domain.Amortization;
import com.loanledger.domain.LoanState;
import com.loanledger.domain.LoanStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Deterministic synthetic data: 50 loans boarded in December 2024 with up to 12 months of 2025
 * payments, posted through the same {@link LoanState} rules as the API. A migration, so it runs
 * exactly once however many replicas start at the same time.
 */
public class V2__seed_demo_data extends BaseJavaMigration {

    private static final String[] FIRST = {"Avery", "Blake", "Casey", "Dakota", "Emerson", "Finley", "Harper", "Jordan", "Kai", "Logan"};
    private static final String[] LAST = {"Alvarez", "Brooks", "Chen", "Dubois", "Evans", "Fischer", "Gupta", "Hayes", "Ito", "Jensen"};
    private static final BigDecimal EXTRA_PRINCIPAL = new BigDecimal("100.00");
    private static final BigDecimal PARTIAL_SHARE = new BigDecimal("0.60");

    @Override
    public void migrate(Context context) throws SQLException {
        Connection db = context.getConnection();
        Random random = new Random(42);
        List<LoanStatus> statuses = statuses();
        Collections.shuffle(statuses, random);
        for (int i = 0; i < statuses.size(); i++) {
            seedLoan(db, random, i, statuses.get(i));
        }
    }

    private static List<LoanStatus> statuses() {
        List<LoanStatus> statuses = new ArrayList<>();
        statuses.addAll(Collections.nCopies(35, LoanStatus.CURRENT));
        statuses.addAll(Collections.nCopies(7, LoanStatus.DELINQUENT));
        statuses.addAll(Collections.nCopies(4, LoanStatus.FORBEARANCE));
        statuses.addAll(Collections.nCopies(2, LoanStatus.MODIFICATION));
        statuses.addAll(Collections.nCopies(2, LoanStatus.RESOLVED));
        return statuses;
    }

    private void seedLoan(Connection db, Random random, int i, LoanStatus status) throws SQLException {
        String first = FIRST[random.nextInt(FIRST.length)];
        String last = LAST[random.nextInt(LAST.length)];
        long borrowerId = insertReturningId(db,
                "INSERT INTO borrowers (first_name, last_name, email) VALUES (?, ?, ?) RETURNING id",
                first, last, "%s.%s.%02d@example.com".formatted(first, last, i).toLowerCase());

        BigDecimal principal = BigDecimal.valueOf(120_000L + random.nextInt(60) * 10_000L).setScale(2);
        BigDecimal rate = BigDecimal.valueOf(4250 + random.nextInt(13) * 250L, 3);
        int term = i % 4 == 0 ? 180 : 360;
        LocalDate origination = LocalDate.of(2024, 12, 1 + i % 28);
        BigDecimal tax = principal.multiply(BigDecimal.valueOf(100 + random.nextInt(50), 4)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal insurance = BigDecimal.valueOf(900 + random.nextInt(1500)).setScale(2);
        BigDecimal monthlyEscrow = Amortization.monthlyEscrow(tax, insurance);
        BigDecimal monthlyPayment = Amortization.monthlyPayment(principal, rate, term);
        LoanState.Terms terms = new LoanState.Terms(rate, monthlyPayment, monthlyEscrow, origination.plusMonths(term));

        // Replay the payments first so the loan row is inserted with its final state.
        LoanState state = LoanState.opening(principal, origination, terms);
        int fullPayments = switch (status) {
            case CURRENT, RESOLVED -> 12;
            case DELINQUENT -> 9;
            case FORBEARANCE, MODIFICATION -> 8;
        };
        boolean overpays = status == LoanStatus.CURRENT && i % 5 == 0;
        List<Object[]> rows = new ArrayList<>();
        BigDecimal escrowBalance = BigDecimal.ZERO.setScale(2);
        for (int n = 0; n <= fullPayments; n++) {
            BigDecimal due = state.interestDue().add(state.principalDue()).add(state.escrowDue());
            BigDecimal amount;
            if (n < fullPayments) {
                amount = overpays ? due.add(EXTRA_PRINCIPAL) : due;
            } else if (status == LoanStatus.DELINQUENT) {
                amount = due.multiply(PARTIAL_SHARE).setScale(2, RoundingMode.HALF_UP); // the last one fell short
            } else {
                break;
            }
            LocalDate paid = state.nextDueDate().plusDays(random.nextInt(6));
            LoanState.Posting posting = state.apply(amount, terms);
            LoanState.Allocation a = posting.allocation();
            rows.add(new Object[] {Date.valueOf(paid), amount, a.principal(), a.interest(), a.escrow()});
            escrowBalance = escrowBalance.add(a.escrow());
            state = posting.after();
        }

        long loanId = insertReturningId(db, """
                INSERT INTO loans (borrower_id, principal, annual_rate, term_months, origination_date, status,
                    monthly_payment, current_balance, next_due_date, interest_due, principal_due, escrow_due)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                borrowerId, principal, rate, term, Date.valueOf(origination), status.name(), monthlyPayment,
                state.balance(), Date.valueOf(state.nextDueDate()), state.interestDue(), state.principalDue(), state.escrowDue());
        insertReturningId(db, """
                INSERT INTO escrow_accounts (loan_id, balance, monthly_amount, annual_tax, annual_insurance)
                VALUES (?, ?, ?, ?, ?) RETURNING id""",
                loanId, escrowBalance, monthlyEscrow, tax, insurance);
        for (Object[] row : rows) {
            insertReturningId(db, """
                    INSERT INTO payments (loan_id, payment_date, amount, principal_amount, interest_amount, escrow_amount)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
                    loanId, row[0], row[1], row[2], row[3], row[4]);
        }
        if (status == LoanStatus.FORBEARANCE || status == LoanStatus.MODIFICATION || status == LoanStatus.RESOLVED) {
            Timestamp opened = Timestamp.valueOf(LocalDateTime.of(2025, 9, 15, 10, 0));
            Timestamp resolved = status == LoanStatus.RESOLVED ? Timestamp.valueOf(LocalDateTime.of(2025, 12, 20, 16, 30)) : null;
            insertReturningId(db, """
                    INSERT INTO loss_mitigation_cases (loan_id, status, notes, opened_at, updated_at, resolved_at)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
                    loanId, status.name(), "Synthetic case: hardship reported by borrower.", opened,
                    resolved == null ? opened : resolved, resolved);
        }
    }

    private static long insertReturningId(Connection db, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}
