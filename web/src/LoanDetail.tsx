import { FormEvent, useCallback, useEffect, useState } from "react";
import { api, Loan, LossMitCase, money, NEXT_STATUSES, Payment, Schedule } from "./api";
import { StatusChip } from "./App";
import BalanceChart from "./BalanceChart";

const today = () => new Date().toLocaleDateString("en-CA"); // YYYY-MM-DD in local time

/** Returns the first problem with the form, or "" when it can be submitted. */
export function validatePayment(amountText: string, date: string, loan: Loan): string {
  const payoff = loan.currentBalance + loan.interestDue + loan.escrowDue;
  if (!/^\d+(\.\d{1,2})?$/.test(amountText)) return "Enter an amount in dollars and cents.";
  const amount = Number(amountText);
  if (amount <= 0) return "Amount must be more than zero.";
  if (amount > payoff) return `Amount is above the payoff amount of ${money(payoff)}.`;
  if (!date) return "Choose a payment date.";
  if (date > today()) return "Payment date can't be in the future.";
  if (date < loan.originationDate) return "Payment date can't be before origination.";
  return "";
}

function PaymentForm({ loan, onPosted }: { loan: Loan; onPosted: () => void }) {
  const due = loan.interestDue + loan.principalDue + loan.escrowDue;
  const [amount, setAmount] = useState(due.toFixed(2));
  const [date, setDate] = useState(today());
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const problem = validatePayment(amount, date, loan);
    setError(problem);
    if (problem) return;
    setBusy(true);
    try {
      await api.postPayment(loan.id, Number(amount), date);
      onPosted();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <h2>Post payment</h2>
      <p className="muted">Amount due: {money(due)}</p>
      <label>
        Amount
        <input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
      </label>
      <label>
        Date
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </label>
      {error && <p role="alert" className="error">{error}</p>}
      <button disabled={busy}>{busy ? "Posting…" : "Post payment"}</button>
    </form>
  );
}

function LossMitPanel({ loan, cases, onChanged }: { loan: Loan; cases: LossMitCase[]; onChanged: () => void }) {
  const open = cases.find((c) => !c.resolvedAt);
  const [error, setError] = useState("");

  async function run(action: Promise<unknown>) {
    setError("");
    try {
      await action;
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const canOpen = !open && (loan.status === "CURRENT" || loan.status === "DELINQUENT");
  return (
    <section>
      <h2>Loss mitigation</h2>
      {open && (
        <>
          <p>Case #{open.id} is open. {open.notes}</p>
          <div className="toolbar">
            {NEXT_STATUSES[open.status].map((next) => (
              <button key={next} onClick={() => run(api.updateCase(open.id, next))}>Move to {next}</button>
            ))}
          </div>
        </>
      )}
      {canOpen && <button onClick={() => run(api.openCase(loan.id, "Opened from dashboard"))}>Open case</button>}
      {!open && !canOpen && <p className="muted">No open case. This loan is {loan.status}.</p>}
      {error && <p role="alert" className="error">{error}</p>}
    </section>
  );
}

export default function LoanDetail({ id }: { id: number }) {
  const [loan, setLoan] = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [payments, setPayments] = useState<Payment[]>([]);
  const [cases, setCases] = useState<LossMitCase[]>([]);
  const [error, setError] = useState("");

  const refresh = useCallback(() => {
    Promise.all([api.loan(id), api.payments(id), api.cases(id)])
      .then(([l, p, c]) => {
        setLoan(l);
        setPayments(p.content);
        setCases(c);
      })
      .catch((e: Error) => setError(e.message));
  }, [id]);

  useEffect(() => {
    refresh();
    api.schedule(id).then(setSchedule).catch((e: Error) => setError(e.message));
  }, [id, refresh]);

  if (error) return <p role="alert" className="error">{error}</p>;
  if (!loan || !schedule) return <p>Loading…</p>;

  const paidOff = loan.currentBalance === 0 && loan.escrowDue === 0;
  return (
    <>
      <p><a href="#/">← All loans</a></p>
      <div className="title-row">
        <h2>{loan.loanNumber} · {loan.borrowerName}</h2>
        <StatusChip status={loan.status} />
      </div>
      <dl className="facts">
        <div><dt>Balance</dt><dd>{money(loan.currentBalance)}</dd></div>
        <div><dt>Original principal</dt><dd>{money(loan.principal)}</dd></div>
        <div><dt>Rate / term</dt><dd>{loan.annualRate.toFixed(3)}% / {loan.termMonths} mo</dd></div>
        <div><dt>Principal and interest</dt><dd>{money(loan.monthlyPayment)}</dd></div>
        <div><dt>Next due</dt><dd>{loan.nextDueDate}</dd></div>
        <div><dt>Escrow balance</dt><dd>{money(loan.escrow?.balance ?? 0)}</dd></div>
      </dl>

      <BalanceChart rows={schedule.rows} />

      <div className="columns">
        {paidOff ? <p>This loan is paid off.</p> : <PaymentForm key={loan.currentBalance + loan.escrowDue} loan={loan} onPosted={refresh} />}
        <LossMitPanel loan={loan} cases={cases} onChanged={refresh} />
      </div>

      <h2>Recent payments</h2>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Date</th><th className="num">Amount</th><th className="num">Interest</th><th className="num">Principal</th><th className="num">Escrow</th></tr>
          </thead>
          <tbody>
            {payments.map((p) => (
              <tr key={p.id}>
                <td>{p.paymentDate}</td>
                <td className="num">{money(p.amount)}</td>
                <td className="num">{money(p.interest)}</td>
                <td className="num">{money(p.principal)}</td>
                <td className="num">{money(p.escrow)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2>Amortization schedule</h2>
      <p className="muted">Total interest over the term: {money(schedule.totalInterest)}</p>
      <div className="table-wrap schedule">
        <table>
          <thead>
            <tr><th className="num">#</th><th>Due</th><th className="num">Payment</th><th className="num">Interest</th><th className="num">Principal</th><th className="num">Balance</th></tr>
          </thead>
          <tbody>
            {schedule.rows.map((r) => (
              <tr key={r.number}>
                <td className="num">{r.number}</td>
                <td>{r.dueDate}</td>
                <td className="num">{money(r.payment)}</td>
                <td className="num">{money(r.interest)}</td>
                <td className="num">{money(r.principal)}</td>
                <td className="num">{money(r.balance)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
