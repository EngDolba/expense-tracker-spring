/** The JSON the Spring Boot API returns for each page; money arrives as plain numbers. */

import type { CategoryNode } from "@/lib/categories";

export type Category = CategoryNode & { parentId: string | null; order: number; archived: boolean };

export type ExpenseType = "EXPENSE" | "INCOME";

export type ExpenseEntry = {
  id: string;
  type: ExpenseType;
  amount: number;
  date: string;
  categoryId: string | null;
  category: Category | null;
  recipient: string;
  note: string;
  tags: string[];
  sourceCode: string | null;
  importBatchId: string | null;
  sourceRow: number | null;
  createdAt: string;
  updatedAt: string;
};

export type Rule = {
  id: string;
  keyword: string;
  categoryId: string | null;
  category: Category | null;
  tags: string[];
  priority: number;
  enabled: boolean;
};

export type ExpensesPage = { entries: ExpenseEntry[]; categories: Category[]; rules: Rule[] };

export type CategoriesPage = {
  categories: Category[];
  codes: Array<{ id: string; code: string; category: Category }>;
};

export type ObligationType = "OUTLAY" | "REIMBURSEMENT";

export type ObligationsPage = {
  parties: Array<{
    id: string;
    name: string;
    note: string;
    archived: boolean;
    outstanding: number;
    createdAt: string;
    updatedAt: string;
  }>;
  cases: Array<{ id: string; partyId: string; name: string; note: string; closed: boolean; createdAt: string }>;
  entries: Array<{
    id: string;
    partyId: string;
    party: { id: string; name: string };
    caseId: string | null;
    case: { id: string; name: string } | null;
    type: ObligationType;
    amount: number;
    date: string;
    categoryId: string | null;
    category: Category | null;
    note: string;
    tags: string[];
    createdAt: string;
  }>;
  categories: Category[];
};

export type AccountEventType = "INCREASE" | "DECREASE" | "TRANSFER_IN" | "TRANSFER_OUT" | "ADJUSTMENT";

export type AccountsPage = {
  accounts: Array<{
    id: string;
    name: string;
    description: string;
    currency: string;
    openingBalance: number;
    archived: boolean;
    balance: number;
    createdAt: string;
    events: Array<{
      id: string;
      accountId: string;
      counterpartyAccountId: string | null;
      type: AccountEventType;
      amount: number;
      date: string;
      note: string;
      transferGroupId: string | null;
      createdAt: string;
    }>;
  }>;
  rates: Array<{ id: string; currency: string; date: string; gelRate: number }>;
};

export type DashboardData = {
  expenseTotal: number;
  incomeTotal: number;
  expenseCount: number;
  outstanding: number;
  accountSummaries: Array<{ id: string; name: string; currency: string; balance: number; gelValue: number | null }>;
  accountGelTotal: number;
  missingRates: string[];
  categories: Category[];
  categoryTotals: Record<string, { amount: number; count: number }>;
  trend: Array<{ month: string; expenses: number; income: number }>;
};

export type ImportStatus = "STAGED" | "COMMITTED" | "FAILED";

export type ImportBatch = {
  id: string;
  fileName: string;
  status: ImportStatus;
  totalRows: number;
  expenseRows: number;
  incomeRows: number;
  errorRows: number;
  mappingCount: number;
  sourceSheet: string | null;
  fileHash: string;
  committedAt: string | null;
  createdAt: string;
};

export type ParsedImportRow = {
  rowNumber: number;
  type: ExpenseType;
  amount: number;
  date: string;
  categoryPath: string[];
  recipient: string;
  note: string;
  tags: string[];
  sourceCode: string;
  errors: string[];
};

export type ImportDetail = {
  batch: ImportBatch;
  rows: Array<{ id: string; rowNumber: number; normalized: ParsedImportRow; errors: string[] }>;
};

export type Variation = {
  id: string;
  name: string;
  note: string;
  order: number;
  recommendedWeight: number;
  recommendedReps: number;
  recommendedSets: number | null;
};

export type FitnessPageData = {
  exercises: Array<{
    id: string;
    name: string;
    muscleGroup: string;
    note: string;
    recommendedWeight: number;
    recommendedReps: number;
    recommendedSets: number | null;
    variations: Variation[];
  }>;
  plans: Array<{
    id: string;
    name: string;
    description: string;
    order: number;
    days: Array<{
      id: string;
      name: string;
      order: number;
      planExercises: Array<{
        id: string;
        setCount: number;
        note: string;
        supersetGroup: number | null;
        target: { weight: number; reps: number; setCount: number };
        exercise: {
          id: string;
          name: string;
          muscleGroup: string;
          recommendedWeight: number;
          recommendedReps: number;
          recommendedSets: number | null;
          variations: Variation[];
        };
      }>;
    }>;
  }>;
  nextPlanDayId: string | null;
  activeWorkouts: Array<{
    id: string;
    planDayId: string;
    planDayName: string;
    planName: string;
    startedAt: string;
    totalSets: number;
    completedSets: number;
  }>;
  workouts: Array<{
    id: string;
    note: string;
    startedAt: string;
    completedAt: string;
    planDay: { id: string; name: string; planName: string };
    volume: number;
    exercises: Array<{
      id: string;
      order: number;
      supersetGroup: number | null;
      targetWeight: number;
      targetReps: number;
      perceivedEffort: number;
      exercise: { id: string; name: string };
      variationName: string | null;
      sets: Array<{ id: string; setNumber: number; weight: number; reps: number }>;
    }>;
  }>;
};
