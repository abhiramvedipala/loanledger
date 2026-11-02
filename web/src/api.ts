export type LoanStatus = "CURRENT" | "DELINQUENT" | "FORBEARANCE" | "MODIFICATION" | "RESOLVED";

export const STATUSES: LoanStatus[] = ["CURRENT", "DELINQUENT", "FORBEARANCE", "MODIFICATION", "RESOLVED"];

// Mirrors LoanStatus.next() on the server, which is what actually enforces it.
export const NEXT_STATUSES: Record<LoanStatus, LoanStatus[]> = {
  CURRENT: ["DELINQUENT"],
  DELINQUENT: ["FORBEARANCE", "MODIFICATION"],
  FORBEARANCE: ["RESOLVED"],
  MODIFICATION: ["RESOLVED"],
  RESOLVED: [],
};

export interface Loan {
  id: number;
  loanNumber: string;
  borrowerName: string;
  borrowerEmail: string;
  principal: number;
  annualRate: number;
  termMonths: number;
  originationDate: string;
  status: LoanStatus;
  monthlyPayment: number;
  currentBalance: number;
  nextDueDate: string;
  interestDue: number;
  principalDue: number;
  escrowDue: number;
  escrow: { balance: number; monthlyAmount: number; annualTax: number; annualInsurance: number } | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ScheduleRow {
  number: number;
  dueDate: string;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
}

export interface Schedule {
  loanId: number;
  monthlyPayment: number;
  totalInterest: number;
  totalPaid: number;
  rows: ScheduleRow[];
}

export interface Payment {
  id: number;
  paymentDate: string;
  amount: number;
  interest: number;
  principal: number;
  escrow: number;
}

export interface LossMitCase {
  id: number;
  status: LoanStatus;
  notes: string | null;
  openedAt: string;
  resolvedAt: string | null;
}

async function request<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    method,
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(data?.detail ?? `Request failed (${response.status})`);
  }
  return data as T;
}

export const api = {
  loans: (page: number, status: string) =>
    request<Page<Loan>>(`/loans?page=${page}&size=15${status ? `&status=${status}` : ""}`),
  loan: (id: number) => request<Loan>(`/loans/${id}`),
  schedule: (id: number) => request<Schedule>(`/loans/${id}/schedule`),
  payments: (id: number) => request<Page<Payment>>(`/loans/${id}/payments?size=12`),
  postPayment: (id: number, amount: number, paymentDate: string) =>
    request(`/loans/${id}/payments`, "POST", { amount, paymentDate }),
  cases: (id: number) => request<LossMitCase[]>(`/loans/${id}/loss-mitigation-cases`),
  openCase: (id: number, notes: string) => request(`/loans/${id}/loss-mitigation-cases`, "POST", { notes }),
  updateCase: (caseId: number, status: LoanStatus) =>
    request(`/loss-mitigation-cases/${caseId}`, "PATCH", { status }),
};

const dollars = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
export const money = (value: number) => dollars.format(value);
