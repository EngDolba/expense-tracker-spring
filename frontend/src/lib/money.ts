type Numeric = number | string | { toString(): string };

export function toNumber(value: Numeric | null | undefined) {
  if (value == null) return 0;
  const parsed = Number(value.toString());
  return Number.isFinite(parsed) ? parsed : 0;
}

export function formatMoney(
  value: Numeric,
  currency = "GEL",
  locale = "ka-GE",
) {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(toNumber(value));
}

export type AccountEventLike = {
  type:
    | "INCREASE"
    | "DECREASE"
    | "TRANSFER_IN"
    | "TRANSFER_OUT"
    | "ADJUSTMENT";
  amount: Numeric;
};

export function accountEventImpact(event: AccountEventLike) {
  const amount = toNumber(event.amount);
  switch (event.type) {
    case "INCREASE":
    case "TRANSFER_IN":
      return Math.abs(amount);
    case "DECREASE":
    case "TRANSFER_OUT":
      return -Math.abs(amount);
    case "ADJUSTMENT":
      return amount;
  }
}

export function accountBalance(
  openingBalance: Numeric,
  events: AccountEventLike[],
) {
  return (
    toNumber(openingBalance) +
    events.reduce((total, event) => total + accountEventImpact(event), 0)
  );
}

export function obligationOutstanding(
  entries: Array<{ type: "OUTLAY" | "REIMBURSEMENT"; amount: Numeric }>,
) {
  return entries.reduce(
    (total, entry) =>
      total +
      (entry.type === "OUTLAY"
        ? Math.abs(toNumber(entry.amount))
        : -Math.abs(toNumber(entry.amount))),
    0,
  );
}

export function convertToGel(balance: Numeric, gelRate?: Numeric | null) {
  if (gelRate == null) return null;
  return toNumber(balance) * toNumber(gelRate);
}
