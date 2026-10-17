import { BranchingLedger } from "@/components/branching-ledger";
import { TrendChart } from "@/components/trend-chart";
import { ActivitySpine, DatabaseNotice, PageHeader, StatCard, SummaryTape, WarningNotice } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";
import { formatMoney } from "@/lib/money";
import { useDashboardData } from "@/lib/queries";

export default function DashboardPage() {
  useTitle("Overview");
  const { locale, t } = useDictionary();
  const { data, loading, unavailable } = useDashboardData();
  if (loading) return <PageLoading />;
  const numberLocale = locale === "ka" ? "ka-GE" : "en-US";

  return (
    <>
      <PageHeader
        eyebrow={locale === "ka" ? "სამი დამოუკიდებელი ჟურნალი" : "Three independent ledgers"}
        title={t.dashboard}
        description={
          locale === "ka"
            ? "ხარჯები, ვალდებულებები და ანგარიშები ერთმანეთისგან გამიჯნულად ინახება."
            : "Expenses, obligations, and accounts remain financially independent."
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      <SummaryTape>
        <StatCard
          label={locale === "ka" ? "პირადი ხარჯები" : "Personal expenses"}
          value={formatMoney(data.expenseTotal, "GEL", numberLocale)}
          detail={`${data.expenseCount} ${locale === "ka" ? "ჩანაწერი" : "entries"}`}
        />
        <StatCard
          label={locale === "ka" ? "შემოსავალი" : "Income"}
          value={formatMoney(data.incomeTotal, "GEL", numberLocale)}
          tone="amber"
          detail={locale === "ka" ? "ხარჯების ჟურნალში" : "Expense ledger only"}
        />
        <StatCard
          label={locale === "ka" ? "დასაბრუნებელი თანხა" : "Outstanding obligations"}
          value={formatMoney(data.outstanding, "GEL", numberLocale)}
          tone="rust"
          detail={locale === "ka" ? "ვალდებულებების ჟურნალი" : "Obligations ledger"}
        />
        <StatCard
          label={locale === "ka" ? "ანგარიშების ჯამი" : "Account total"}
          value={formatMoney(data.accountGelTotal, "GEL", numberLocale)}
          tone="blue"
          detail={locale === "ka" ? "ბოლო ხელმისაწვდომი კურსებით" : "Using latest available rates"}
        />
      </SummaryTape>
      {data.missingRates.length ? (
        <div className="mb-4">
          <WarningNotice>
            {locale === "ka" ? "კურსი აკლია: " : "Missing exchange rate: "}
            {data.missingRates.join(", ")}
          </WarningNotice>
        </div>
      ) : null}
      <div className="dashboard-grid finance-analysis-grid">
        <div className="stack">
          <section className="panel analysis-primary">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "თვიური მოძრაობა" : "Monthly movement"}</h2>
                <p>{locale === "ka" ? "ხარჯები და შემოსავალი, 12 თვე" : "Expenses and income, last 12 months"}</p>
              </div>
              <div className="flex gap-2">
                <span className="tag">{locale === "ka" ? "ხარჯი" : "Expense"}</span>
                <span className="tag tag-amber">{locale === "ka" ? "შემოსავალი" : "Income"}</span>
              </div>
            </div>
            <TrendChart data={data.trend} emptyLabel={t.noData} />
          </section>
          <section className="panel financial-position">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ანგარიშები" : "Accounts"}</h2>
                <p>{locale === "ka" ? "ნაშთები საკუთარ ვალუტაში" : "Balances in native currencies"}</p>
              </div>
            </div>
            {data.accountSummaries.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{locale === "ka" ? "ანგარიში" : "Account"}</th>
                      <th>{locale === "ka" ? "ვალუტა" : "Currency"}</th>
                      <th className="numeric">{locale === "ka" ? "ნაშთი" : "Balance"}</th>
                      <th className="numeric">GEL</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.accountSummaries.map((account) => (
                      <tr key={account.id}>
                        <td className="font-medium">{account.name}</td>
                        <td>{account.currency}</td>
                        <td className="numeric">
                          {formatMoney(account.balance, account.currency, numberLocale)}
                        </td>
                        <td className="numeric">
                          {account.gelValue == null
                            ? "—"
                            : formatMoney(account.gelValue, "GEL", numberLocale)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
            <div className="analysis-spine-row">
              <ActivitySpine
                label={locale === "ka" ? "ანგარიშები" : "Accounts tracked"}
                value={String(data.accountSummaries.length)}
                detail={locale === "ka" ? "ბალანსები ვალუტებში" : "Native balances"}
              />
              <ActivitySpine
                label={locale === "ka" ? "კურსის გარეშე" : "Currencies missing rates"}
                value={String(data.missingRates.length)}
                detail={data.missingRates.length ? data.missingRates.join(", ") : locale === "ka" ? "ყველა აქტუალურია" : "All current"}
                tone={data.missingRates.length ? "amber" : "green"}
              />
            </div>
          </section>
        </div>
        <section className="panel">
          <div className="panel-header">
            <div>
              <h2>{locale === "ka" ? "ხარჯების განშტოება" : "Expense branches"}</h2>
              <p>{locale === "ka" ? "გაშალეთ ნებისმიერი დონე" : "Expand any category level"}</p>
            </div>
          </div>
          <BranchingLedger
            categories={data.categories}
            totals={data.categoryTotals}
            storageKey="dashboard-category-tree"
            locale={numberLocale}
            emptyLabel={t.noData}
          />
        </section>
      </div>
    </>
  );
}
