import { useEffect, useState } from "react";
import { api, Loan, LoanStatus, money, Page, STATUSES } from "./api";
import LoanDetail from "./LoanDetail";

const STATUS_ICON: Record<LoanStatus, string> = {
  CURRENT: "✓",
  DELINQUENT: "!",
  FORBEARANCE: "‖",
  MODIFICATION: "✎",
  RESOLVED: "●",
};

export function StatusChip({ status }: { status: LoanStatus }) {
  return (
    <span className={`chip chip-${status.toLowerCase()}`}>
      <span aria-hidden="true">{STATUS_ICON[status]}</span> {status}
    </span>
  );
}

// Hash routing: Spring serves one index.html, so links need no server fallback.
function useHash() {
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const update = () => setHash(window.location.hash);
    window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, []);
  return hash;
}

function LoansTable() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [loans, setLoans] = useState<Page<Loan> | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.loans(page, status).then(setLoans).catch((e: Error) => setError(e.message));
  }, [page, status]);

  if (error) return <p role="alert" className="error">{error}</p>;
  if (!loans) return <p>Loading…</p>;

  return (
    <>
      <div className="toolbar">
        <label>
          Status{" "}
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
        <span className="muted">{loans.totalElements} loans</span>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Loan</th>
              <th>Borrower</th>
              <th>Status</th>
              <th className="num">Balance</th>
              <th className="num">Rate</th>
              <th>Next due</th>
            </tr>
          </thead>
          <tbody>
            {loans.content.map((loan) => (
              <tr key={loan.id}>
                <td><a href={`#/loans/${loan.id}`}>{loan.loanNumber}</a></td>
                <td>{loan.borrowerName}</td>
                <td><StatusChip status={loan.status} /></td>
                <td className="num">{money(loan.currentBalance)}</td>
                <td className="num">{loan.annualRate.toFixed(3)}%</td>
                <td>{loan.nextDueDate}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="toolbar">
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <span className="muted">Page {loans.page + 1} of {Math.max(loans.totalPages, 1)}</span>
        <button disabled={page + 1 >= loans.totalPages} onClick={() => setPage(page + 1)}>Next</button>
      </div>
    </>
  );
}

export default function App() {
  const match = useHash().match(/^#\/loans\/(\d+)$/);
  return (
    <main>
      <header>
        <h1><a href="#/">LoanLedger</a></h1>
        <span className="muted">Mortgage servicing · synthetic data</span>
      </header>
      {match ? <LoanDetail key={match[1]} id={Number(match[1])} /> : <LoansTable />}
    </main>
  );
}
