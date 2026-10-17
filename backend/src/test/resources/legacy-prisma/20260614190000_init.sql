-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "ExpenseType" AS ENUM ('EXPENSE', 'INCOME');

-- CreateEnum
CREATE TYPE "ObligationType" AS ENUM ('OUTLAY', 'REIMBURSEMENT');

-- CreateEnum
CREATE TYPE "AccountEventType" AS ENUM ('INCREASE', 'DECREASE', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT');

-- CreateEnum
CREATE TYPE "ImportStatus" AS ENUM ('STAGED', 'COMMITTED', 'FAILED');

-- CreateTable
CREATE TABLE "Category" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "parentId" TEXT,
    "order" INTEGER NOT NULL DEFAULT 0,
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Category_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CategoryCode" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "categoryId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CategoryCode_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExpenseEntry" (
    "id" TEXT NOT NULL,
    "type" "ExpenseType" NOT NULL,
    "amount" DECIMAL(18,2) NOT NULL,
    "date" DATE NOT NULL,
    "categoryId" TEXT,
    "recipient" TEXT NOT NULL DEFAULT '',
    "note" TEXT NOT NULL DEFAULT '',
    "tags" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "sourceCode" TEXT,
    "importBatchId" TEXT,
    "sourceRow" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExpenseEntry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CategorizationRule" (
    "id" TEXT NOT NULL,
    "keyword" TEXT NOT NULL,
    "categoryId" TEXT,
    "tags" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "priority" INTEGER NOT NULL DEFAULT 0,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CategorizationRule_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ObligationParty" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "note" TEXT NOT NULL DEFAULT '',
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ObligationParty_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ObligationCase" (
    "id" TEXT NOT NULL,
    "partyId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "note" TEXT NOT NULL DEFAULT '',
    "closed" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ObligationCase_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ObligationEntry" (
    "id" TEXT NOT NULL,
    "partyId" TEXT NOT NULL,
    "caseId" TEXT,
    "type" "ObligationType" NOT NULL,
    "amount" DECIMAL(18,2) NOT NULL,
    "date" DATE NOT NULL,
    "categoryId" TEXT,
    "note" TEXT NOT NULL DEFAULT '',
    "tags" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ObligationEntry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FinancialAccount" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL DEFAULT '',
    "currency" VARCHAR(3) NOT NULL,
    "openingBalance" DECIMAL(18,2) NOT NULL,
    "archived" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "FinancialAccount_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AccountEvent" (
    "id" TEXT NOT NULL,
    "accountId" TEXT NOT NULL,
    "counterpartyAccountId" TEXT,
    "type" "AccountEventType" NOT NULL,
    "amount" DECIMAL(18,2) NOT NULL,
    "date" DATE NOT NULL,
    "note" TEXT NOT NULL DEFAULT '',
    "transferGroupId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AccountEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExchangeRate" (
    "id" TEXT NOT NULL,
    "currency" VARCHAR(3) NOT NULL,
    "date" DATE NOT NULL,
    "gelRate" DECIMAL(18,8) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExchangeRate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ImportBatch" (
    "id" TEXT NOT NULL,
    "fileHash" TEXT NOT NULL,
    "fileName" TEXT NOT NULL,
    "status" "ImportStatus" NOT NULL DEFAULT 'STAGED',
    "totalRows" INTEGER NOT NULL DEFAULT 0,
    "expenseRows" INTEGER NOT NULL DEFAULT 0,
    "incomeRows" INTEGER NOT NULL DEFAULT 0,
    "errorRows" INTEGER NOT NULL DEFAULT 0,
    "mappingCount" INTEGER NOT NULL DEFAULT 0,
    "sourceSheet" TEXT,
    "dictionary" JSONB,
    "committedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ImportBatch_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ImportRow" (
    "id" TEXT NOT NULL,
    "batchId" TEXT NOT NULL,
    "rowNumber" INTEGER NOT NULL,
    "normalized" JSONB NOT NULL,
    "errors" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ImportRow_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Category_parentId_order_idx" ON "Category"("parentId", "order");

-- CreateIndex
CREATE UNIQUE INDEX "Category_parentId_name_key" ON "Category"("parentId", "name");

-- CreateIndex
CREATE UNIQUE INDEX "CategoryCode_code_key" ON "CategoryCode"("code");

-- CreateIndex
CREATE INDEX "ExpenseEntry_date_idx" ON "ExpenseEntry"("date");

-- CreateIndex
CREATE INDEX "ExpenseEntry_categoryId_idx" ON "ExpenseEntry"("categoryId");

-- CreateIndex
CREATE INDEX "ExpenseEntry_importBatchId_idx" ON "ExpenseEntry"("importBatchId");

-- CreateIndex
CREATE INDEX "CategorizationRule_enabled_priority_idx" ON "CategorizationRule"("enabled", "priority");

-- CreateIndex
CREATE UNIQUE INDEX "ObligationParty_name_key" ON "ObligationParty"("name");

-- CreateIndex
CREATE INDEX "ObligationCase_partyId_closed_idx" ON "ObligationCase"("partyId", "closed");

-- CreateIndex
CREATE UNIQUE INDEX "ObligationCase_partyId_name_key" ON "ObligationCase"("partyId", "name");

-- CreateIndex
CREATE INDEX "ObligationEntry_partyId_date_idx" ON "ObligationEntry"("partyId", "date");

-- CreateIndex
CREATE INDEX "ObligationEntry_caseId_idx" ON "ObligationEntry"("caseId");

-- CreateIndex
CREATE INDEX "ObligationEntry_categoryId_idx" ON "ObligationEntry"("categoryId");

-- CreateIndex
CREATE UNIQUE INDEX "FinancialAccount_name_key" ON "FinancialAccount"("name");

-- CreateIndex
CREATE INDEX "FinancialAccount_archived_idx" ON "FinancialAccount"("archived");

-- CreateIndex
CREATE INDEX "AccountEvent_accountId_date_idx" ON "AccountEvent"("accountId", "date");

-- CreateIndex
CREATE INDEX "AccountEvent_transferGroupId_idx" ON "AccountEvent"("transferGroupId");

-- CreateIndex
CREATE INDEX "ExchangeRate_date_idx" ON "ExchangeRate"("date");

-- CreateIndex
CREATE UNIQUE INDEX "ExchangeRate_currency_date_key" ON "ExchangeRate"("currency", "date");

-- CreateIndex
CREATE UNIQUE INDEX "ImportBatch_fileHash_key" ON "ImportBatch"("fileHash");

-- CreateIndex
CREATE INDEX "ImportRow_batchId_idx" ON "ImportRow"("batchId");

-- CreateIndex
CREATE UNIQUE INDEX "ImportRow_batchId_rowNumber_key" ON "ImportRow"("batchId", "rowNumber");

-- AddForeignKey
ALTER TABLE "Category" ADD CONSTRAINT "Category_parentId_fkey" FOREIGN KEY ("parentId") REFERENCES "Category"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CategoryCode" ADD CONSTRAINT "CategoryCode_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "Category"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExpenseEntry" ADD CONSTRAINT "ExpenseEntry_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "Category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExpenseEntry" ADD CONSTRAINT "ExpenseEntry_importBatchId_fkey" FOREIGN KEY ("importBatchId") REFERENCES "ImportBatch"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CategorizationRule" ADD CONSTRAINT "CategorizationRule_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "Category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ObligationCase" ADD CONSTRAINT "ObligationCase_partyId_fkey" FOREIGN KEY ("partyId") REFERENCES "ObligationParty"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ObligationEntry" ADD CONSTRAINT "ObligationEntry_partyId_fkey" FOREIGN KEY ("partyId") REFERENCES "ObligationParty"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ObligationEntry" ADD CONSTRAINT "ObligationEntry_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "ObligationCase"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ObligationEntry" ADD CONSTRAINT "ObligationEntry_categoryId_fkey" FOREIGN KEY ("categoryId") REFERENCES "Category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AccountEvent" ADD CONSTRAINT "AccountEvent_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "FinancialAccount"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AccountEvent" ADD CONSTRAINT "AccountEvent_counterpartyAccountId_fkey" FOREIGN KEY ("counterpartyAccountId") REFERENCES "FinancialAccount"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ImportRow" ADD CONSTRAINT "ImportRow_batchId_fkey" FOREIGN KEY ("batchId") REFERENCES "ImportBatch"("id") ON DELETE CASCADE ON UPDATE CASCADE;
