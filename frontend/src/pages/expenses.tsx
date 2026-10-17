import { Plus, Trash2, WandSparkles } from "lucide-react";
import {
  createExpenseAction,
  createRuleAction,
  deleteExpenseAction,
  deleteRuleAction,
} from "@/app/actions";
import { BranchingLedger } from "@/components/branching-ledger";
import { CategorySelect } from "@/components/category-select";
import { SubmitButton } from "@/components/submit-button";
import { DatabaseNotice, PageHeader, StatCard } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";
import { formatMoney } from "@/lib/money";
import { useExpensePageData } from "@/lib/queries";

export default function ExpensesPage() {
  useTitle("Expenses");
  const { locale, t } = useDictionary();
  const { data, loading, unavailable } = useExpensePageData();
  if (loading) return <PageLoading />;
  const numberLocale = locale === "ka" ? "ka-GE" : "en-US";
  const expenses = data.entries.filter((entry) => entry.type === "EXPENSE");
  const income = data.entries.filter((entry) => entry.type === "INCOME");
  const expenseTotal = expenses.reduce((sum, entry) => sum + entry.amount, 0);
  const incomeTotal = income.reduce((sum, entry) => sum + entry.amount, 0);
  const categoryTotals = data.entries.reduce<
    Record<string, { amount: number; count: number }>
  >((totals, entry) => {
    if (entry.type !== "EXPENSE" || !entry.categoryId) return totals;
    const current = totals[entry.categoryId] ?? { amount: 0, count: 0 };
    current.amount += entry.amount;
    current.count += 1;
    totals[entry.categoryId] = current;
    return totals;
  }, {});

  return (
    <>
      <PageHeader
        eyebrow={locale === "ka" ? "პირადი ჟურნალი" : "Personal ledger"}
        title={t.expenses}
        description={
          locale === "ka"
            ? "შემოსავალი და პირადი ხარჯები. ანგარიშებისა და ვალდებულებებისგან სრულად განცალკევებული."
            : "Income and personal spending, fully separate from accounts and obligations."
        }
        actions={
          <a href="#new-entry" className="button button-primary">
            <Plus size={17} />
            {locale === "ka" ? "ახალი ჩანაწერი" : "New entry"}
          </a>
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      <div className="stats-grid mt-4">
        <StatCard
          label={locale === "ka" ? "ხარჯების ჯამი" : "Total expenses"}
          value={formatMoney(expenseTotal, "GEL", numberLocale)}
          detail={`${expenses.length} ${locale === "ka" ? "ჩანაწერი" : "entries"}`}
        />
        <StatCard
          label={locale === "ka" ? "შემოსავლის ჯამი" : "Total income"}
          value={formatMoney(incomeTotal, "GEL", numberLocale)}
          tone="amber"
          detail={`${income.length} ${locale === "ka" ? "ჩანაწერი" : "entries"}`}
        />
        <StatCard
          label={locale === "ka" ? "წმინდა მოძრაობა" : "Net movement"}
          value={formatMoney(incomeTotal - expenseTotal, "GEL", numberLocale)}
          tone={incomeTotal - expenseTotal >= 0 ? "green" : "rust"}
          detail={locale === "ka" ? "მხოლოდ ამ ჟურნალში" : "Within this ledger only"}
        />
        <StatCard
          label={locale === "ka" ? "ავტო-წესები" : "Auto rules"}
          value={String(data.rules.filter((rule) => rule.enabled).length)}
          tone="blue"
          detail={locale === "ka" ? "აქტიური წესი" : "active rules"}
        />
      </div>

      <div className="dashboard-grid expenses-layout">
        <div className="stack">
          <section className="panel entry-composer" id="new-entry">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "სწრაფი ჩანაწერი" : "Quick entry"}</h2>
                <p>{locale === "ka" ? "კატეგორიის გარეშე ჩანაწერზე წესები ავტომატურად იმუშავებს." : "Rules run automatically when no category is selected."}</p>
              </div>
            </div>
            <form action={createExpenseAction} className="form-grid">
              <label className="field">
                <span>{locale === "ka" ? "ტიპი" : "Type"}</span>
                <select name="type" defaultValue="EXPENSE">
                  <option value="EXPENSE">{locale === "ka" ? "ხარჯი" : "Expense"}</option>
                  <option value="INCOME">{locale === "ka" ? "შემოსავალი" : "Income"}</option>
                </select>
              </label>
              <label className="field">
                <span>{t.amount}</span>
                <input name="amount" type="number" min="0.01" step="0.01" required />
              </label>
              <label className="field">
                <span>{t.date}</span>
                <input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required />
              </label>
              <label className="field">
                <span>{t.category}</span>
                <CategorySelect
                  categories={data.categories}
                  placeholder={locale === "ka" ? "კატეგორია" : "Category"}
                />
              </label>
              <label className="field field-wide">
                <span>{t.recipient}</span>
                <input name="recipient" placeholder={locale === "ka" ? "ვის გადაუხადეთ / ვისგან მიიღეთ" : "Merchant or source"} />
              </label>
              <label className="field">
                <span>{locale === "ka" ? "კოდი" : "Code"}</span>
                <input name="sourceCode" placeholder="3001" />
              </label>
              <label className="field">
                <span>{locale === "ka" ? "ტეგები" : "Tags"}</span>
                <input name="tags" placeholder={locale === "ka" ? "ოჯახი, მოგზაურობა" : "family, travel"} />
              </label>
              <label className="field field-wide">
                <span>{t.note}</span>
                <input name="note" />
              </label>
              <div className="field field-full items-start">
                <SubmitButton>{t.add}</SubmitButton>
              </div>
            </form>
          </section>

          <section className="panel primary-register">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ჩანაწერების რეესტრი" : "Entry register"}</h2>
                <p>{locale === "ka" ? "უახლესი 250 ჩანაწერი" : "Latest 250 entries"}</p>
              </div>
              <a className="button button-secondary" href="/api/exports/expenses">
                CSV
              </a>
            </div>
            {data.entries.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{t.date}</th>
                      <th>{locale === "ka" ? "ტიპი" : "Type"}</th>
                      <th>{t.category}</th>
                      <th>{t.recipient}</th>
                      <th>{t.note}</th>
                      <th className="numeric">{t.amount}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {data.entries.map((entry) => (
                      <tr key={entry.id}>
                        <td className="whitespace-nowrap">{entry.date}</td>
                        <td>
                          <span className={entry.type === "EXPENSE" ? "tag" : "tag tag-amber"}>
                            {entry.type === "EXPENSE"
                              ? locale === "ka" ? "ხარჯი" : "Expense"
                              : locale === "ka" ? "შემოსავალი" : "Income"}
                          </span>
                        </td>
                        <td>{entry.category?.name ?? "—"}</td>
                        <td>{entry.recipient || "—"}</td>
                        <td>
                          <span>{entry.note || "—"}</span>
                          {entry.tags.length ? (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {entry.tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}
                            </div>
                          ) : null}
                        </td>
                        <td className="numeric">
                          <strong style={{ color: entry.type === "EXPENSE" ? "var(--ledger-rust)" : "var(--ledger-green)" }}>
                            {entry.type === "EXPENSE" ? "−" : "+"}
                            {formatMoney(entry.amount, "GEL", numberLocale)}
                          </strong>
                        </td>
                        <td>
                          <form action={deleteExpenseAction}>
                            <input type="hidden" name="id" value={entry.id} />
                            <button className="button button-danger" type="submit" aria-label="Delete">
                              <Trash2 size={15} />
                            </button>
                          </form>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "კატეგორიების ანალიზი" : "Category analysis"}</h2>
                <p>{locale === "ka" ? "ჯამები ყველა დონეზე" : "Rollups at every level"}</p>
              </div>
            </div>
            <BranchingLedger
              categories={data.categories}
              totals={categoryTotals}
              storageKey="expense-category-tree"
              locale={numberLocale}
              emptyLabel={t.noData}
            />
          </section>
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ავტო-კატეგორიზაცია" : "Auto-categorization"}</h2>
                <p>{locale === "ka" ? "მიმღებსა და შენიშვნაში სიტყვის მოძებნა" : "Match keywords in recipient and note"}</p>
              </div>
            </div>
            <details className="form-details">
              <summary><WandSparkles size={15} />{locale === "ka" ? "ახალი წესი" : "New rule"}</summary>
              <div className="details-body">
                <form action={createRuleAction} className="form-grid">
                  <label className="field field-wide">
                    <span>{locale === "ka" ? "საკვანძო სიტყვა" : "Keyword"}</span>
                    <input name="keyword" required />
                  </label>
                  <label className="field field-wide">
                    <span>{t.category}</span>
                    <CategorySelect categories={data.categories} placeholder={locale === "ka" ? "კატეგორია" : "Category"} />
                  </label>
                  <label className="field field-wide">
                    <span>{locale === "ka" ? "ტეგები" : "Tags"}</span>
                    <input name="tags" />
                  </label>
                  <label className="field">
                    <span>{locale === "ka" ? "პრიორიტეტი" : "Priority"}</span>
                    <input name="priority" type="number" defaultValue="0" />
                  </label>
                  <div className="field field-full items-start">
                    <SubmitButton>{t.add}</SubmitButton>
                  </div>
                </form>
              </div>
            </details>
            <div className="mt-4 grid gap-2">
              {data.rules.map((rule) => (
                <div className="flex items-center gap-3 border-b border-black/5 py-2" key={rule.id}>
                  <div className="min-w-0 flex-1">
                    <strong>{rule.keyword}</strong>
                    <div className="meta">
                      {rule.category?.name ?? "—"} · P{rule.priority}
                    </div>
                  </div>
                  <form action={deleteRuleAction}>
                    <input type="hidden" name="id" value={rule.id} />
                    <button className="button button-danger" type="submit" aria-label="Delete rule">
                      <Trash2 size={14} />
                    </button>
                  </form>
                </div>
              ))}
              {!data.rules.length ? <div className="empty-state">{t.noData}</div> : null}
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
