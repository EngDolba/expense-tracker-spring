import { describe, expect, it } from "vitest";
import {
  accountBalance,
  convertToGel,
  obligationOutstanding,
} from "@/lib/money";

describe("isolated ledger calculations", () => {
  it("calculates the obligation example without another ledger", () => {
    expect(
      obligationOutstanding([
        { type: "OUTLAY", amount: 10 },
        { type: "OUTLAY", amount: 20 },
        { type: "REIMBURSEMENT", amount: 25 },
      ]),
    ).toBe(5);
  });

  it("calculates account balances from opening balance and events", () => {
    expect(
      accountBalance(100, [
        { type: "INCREASE", amount: 20 },
        { type: "DECREASE", amount: 5 },
        { type: "TRANSFER_OUT", amount: 15 },
        { type: "ADJUSTMENT", amount: -2 },
      ]),
    ).toBe(98);
  });

  it("converts only when an exchange rate is available", () => {
    expect(convertToGel(100, 2.7)).toBe(270);
    expect(convertToGel(100, null)).toBeNull();
  });
});
