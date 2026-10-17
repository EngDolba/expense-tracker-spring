import { ArrowDownUp, ArrowRightLeft, Coins, Landmark, Plus, Scale } from "lucide-react";
import {
  createAccountAction,
  createAccountEventAction,
  createTransferAction,
  reconcileAccountAction,
  upsertExchangeRateAction,
} from "@/app/actions";
import { SubmitButton } from "@/components/submit-button";
import { DatabaseNotice, PageHeader, StatCard, WarningNotice } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";
import { formatMoney } from "@/lib/money";
import { useAccountPageData } from "@/lib/queries";

const eventLabels = {
  INCREASE: { ka: "ზრდა", en: "Increase", sign: "+" },
  DECREASE: { ka: "შემცირება", en: "Decrease", sign: "−" },
  TRANSFER_IN: { ka: "გადმორიცხვა", en: "Transfer in", sign: "+" },
  TRANSFER_OUT: { ka: "გადარიცხვა", en: "Transfer out", sign: "−" },
  ADJUSTMENT: { ka: "შესწორება", en: "Adjustment", sign: "" },
} as const;

export default function AccountsPage() {
  useTitle("Accounts");
  const { locale, t } = useDictionary();
  const { data, loading, unavailable } = useAccountPageData();
  if (loading) return <PageLoading />;
  const numberLocale = locale === "ka" ? "ka-GE" : "en-US";
  const latestRates = new Map<string, number>([["GEL", 1]]);
  for (const rate of data.rates) {
    if (!latestRates.has(rate.currency)) latestRates.set(rate.currency, rate.gelRate);
  }
  const totalGel = data.accounts.reduce((total, account) => {
    const rate = latestRates.get(account.currency);
    return total + (rate == null ? 0 : account.balance * rate);
  }, 0);
  const missingRates = [...new Set(
    data.accounts.filter((account) => !latestRates.has(account.currency)).map((account) => account.currency),
  )];
  const events = data.accounts
    .flatMap((account) => account.events.map((event) => ({ ...event, account })))
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 250);

  return (
    <>
      <PageHeader
        eyebrow={locale === "ka" ? "დამოუკიდებელი ნაშთები" : "Independent balances"}
        title={t.accounts}
        description={
          locale === "ka"
            ? "შექმენით ნებისმიერი ტიპის ანგარიში. აქ მოძრაობები არ უკავშირდება ხარჯებს ან ვალდებულებებს."
            : "Create any kind of account. Movements here never post to expenses or obligations."
        }
        actions={
          <a className="button button-primary" href="#new-account">
            <Plus size={17} />
            {locale === "ka" ? "ახალი ანგარიში" : "New account"}
          </a>
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      <div className="stats-grid mt-4">
        <StatCard
          label={locale === "ka" ? "ანგარიშების რაოდენობა" : "Accounts"}
          value={String(data.accounts.length)}
          detail={locale === "ka" ? "ყველა ტიპი" : "All user-defined types"}
        />
        <StatCard
          label={locale === "ka" ? "GEL შეფასება" : "GEL valuation"}
          value={formatMoney(totalGel, "GEL", numberLocale)}
          tone="blue"
          detail={locale === "ka" ? "ბოლო ხელმისაწვდომი კურსით" : "Latest available rates"}
        />
        <StatCard
          label={locale === "ka" ? "ვალუტები" : "Currencies"}
          value={String(new Set(data.accounts.map((account) => account.currency)).size)}
          tone="amber"
        />
        <StatCard
          label={locale === "ka" ? "მოძრაობები" : "Movements"}
          value={String(events.length)}
          tone="green"
          detail={locale === "ka" ? "უახლესი რეესტრი" : "Latest register"}
        />
      </div>
      {missingRates.length ? (
        <div className="mb-4">
          <WarningNotice>
            {locale === "ka" ? "GEL კურსი აკლია: " : "Missing GEL rate: "}
            {missingRates.join(", ")}
          </WarningNotice>
        </div>
      ) : null}

      <div className="dashboard-grid accounts-layout">
        <div className="stack">
          <section className="panel primary-register">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ანგარიშების რეესტრი" : "Account register"}</h2>
                <p>{locale === "ka" ? "ნაშთი = საწყისი ნაშთი + მოძრაობები" : "Balance = opening balance + movements"}</p>
              </div>
            </div>
            {data.accounts.length ? (
              <div className="grid gap-3 md:grid-cols-2">
                {data.accounts.map((account) => {
                  const rate = latestRates.get(account.currency);
                  return (
                    <article className="panel" style={{ background: "var(--ledger-sheet-raised)" }} key={account.id}>
                      <div className="flex items-start gap-3">
                        <span className="brand-mark"><Landmark size={18} /></span>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <h3 className="font-semibold">{account.name}</h3>
                              <p className="meta">{account.description || account.currency}</p>
                            </div>
                            <span className="tag">{account.currency}</span>
                          </div>
                          <strong className="money mt-5 block text-xl text-[var(--ledger-green)]">
                            {formatMoney(account.balance, account.currency, numberLocale)}
                          </strong>
                          <div className="meta mt-1">
                            {rate == null
                              ? locale === "ka" ? "GEL კურსი არ არის" : "No GEL rate"
                              : `≈ ${formatMoney(account.balance * rate, "GEL", numberLocale)}`}
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
          </section>

          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "მოძრაობების ისტორია" : "Movement history"}</h2>
                <p>{locale === "ka" ? "ხარჯებისა და ვალდებულებებისგან დამოუკიდებელი" : "Independent from expenses and obligations"}</p>
              </div>
              <a className="button button-secondary" href="/api/exports/accounts">CSV</a>
            </div>
            {events.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{t.date}</th>
                      <th>{locale === "ka" ? "ანგარიში" : "Account"}</th>
                      <th>{locale === "ka" ? "ტიპი" : "Type"}</th>
                      <th>{t.note}</th>
                      <th className="numeric">{t.amount}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.map((event) => {
                      const label = eventLabels[event.type];
                      const negative = ["DECREASE", "TRANSFER_OUT"].includes(event.type) ||
                        (event.type === "ADJUSTMENT" && event.amount < 0);
                      return (
                        <tr key={event.id}>
                          <td>{event.date}</td>
                          <td><strong>{event.account.name}</strong><div className="meta">{event.account.currency}</div></td>
                          <td>{label[locale]}</td>
                          <td>{event.note || "—"}</td>
                          <td className="numeric" style={{ color: negative ? "var(--ledger-rust)" : "var(--ledger-green)" }}>
                            <strong>{negative ? "−" : "+"}{formatMoney(Math.abs(event.amount), event.account.currency, numberLocale)}</strong>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="panel" id="new-account">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ანგარიშის დამატება" : "Add account"}</h2>
                <p>{locale === "ka" ? "სახელი და ვალუტა სრულად თქვენ განსაზღვრეთ" : "Name and currency are entirely user-defined"}</p>
              </div>
            </div>
            <form action={createAccountAction} className="form-grid">
              <label className="field field-wide"><span>{locale === "ka" ? "სახელი" : "Name"}</span><input name="name" required /></label>
              <label className="field"><span>{locale === "ka" ? "ვალუტა" : "Currency"}</span><input name="currency" defaultValue="GEL" minLength={3} maxLength={3} required /></label>
              <label className="field"><span>{locale === "ka" ? "საწყისი ნაშთი" : "Opening balance"}</span><input name="openingBalance" type="number" step="0.01" defaultValue="0" required /></label>
              <label className="field field-full"><span>{locale === "ka" ? "აღწერა" : "Description"}</span><input name="description" /></label>
              <div className="field field-full items-start"><SubmitButton>{t.add}</SubmitButton></div>
            </form>
          </section>

          <details className="form-details">
            <summary><ArrowDownUp size={15} />{locale === "ka" ? "ანგარიშის მოძრაობა" : "Account movement"}</summary>
            <div className="details-body">
              <form action={createAccountEventAction} className="form-grid">
                <label className="field field-wide">
                  <span>{locale === "ka" ? "ანგარიში" : "Account"}</span>
                  <select name="accountId" required defaultValue="">
                    <option value="" disabled>—</option>
                    {data.accounts.map((account) => <option value={account.id} key={account.id}>{account.name} · {account.currency}</option>)}
                  </select>
                </label>
                <label className="field field-wide">
                  <span>{locale === "ka" ? "ტიპი" : "Type"}</span>
                  <select name="type" defaultValue="INCREASE">
                    <option value="INCREASE">{locale === "ka" ? "ზრდა" : "Increase"}</option>
                    <option value="DECREASE">{locale === "ka" ? "შემცირება" : "Decrease"}</option>
                    <option value="ADJUSTMENT">{locale === "ka" ? "ხელით შესწორება" : "Manual adjustment"}</option>
                  </select>
                </label>
                <label className="field field-wide"><span>{t.amount}</span><input name="amount" type="number" step="0.01" required /></label>
                <label className="field field-wide"><span>{t.date}</span><input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
                <label className="field field-full"><span>{t.note}</span><input name="note" /></label>
                <div className="field field-full items-start"><SubmitButton>{t.add}</SubmitButton></div>
              </form>
            </div>
          </details>

          <details className="form-details">
            <summary><ArrowRightLeft size={15} />{locale === "ka" ? "ანგარიშებს შორის გადატანა" : "Transfer between accounts"}</summary>
            <div className="details-body">
              <form action={createTransferAction} className="form-grid">
                <label className="field field-wide"><span>{locale === "ka" ? "საიდან" : "From"}</span><select name="fromAccountId" required defaultValue=""><option value="" disabled>—</option>{data.accounts.map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label>
                <label className="field field-wide"><span>{locale === "ka" ? "სად" : "To"}</span><select name="toAccountId" required defaultValue=""><option value="" disabled>—</option>{data.accounts.map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label>
                <label className="field field-wide"><span>{locale === "ka" ? "გასული თანხა" : "Amount sent"}</span><input name="fromAmount" type="number" min="0.01" step="0.01" required /></label>
                <label className="field field-wide"><span>{locale === "ka" ? "მიღებული თანხა" : "Amount received"}</span><input name="toAmount" type="number" min="0.01" step="0.01" required /></label>
                <label className="field field-wide"><span>{t.date}</span><input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
                <label className="field field-full"><span>{t.note}</span><input name="note" /></label>
                <div className="field field-full items-start"><SubmitButton>{locale === "ka" ? "გადატანა" : "Transfer"}</SubmitButton></div>
              </form>
            </div>
          </details>

          <details className="form-details">
            <summary><Scale size={15} />{locale === "ka" ? "ნაშთის შეჯერება" : "Reconcile balance"}</summary>
            <div className="details-body">
              <form action={reconcileAccountAction} className="form-grid">
                <label className="field field-wide"><span>{locale === "ka" ? "ანგარიში" : "Account"}</span><select name="accountId" required defaultValue=""><option value="" disabled>—</option>{data.accounts.map((account) => <option value={account.id} key={account.id}>{account.name}</option>)}</select></label>
                <label className="field field-wide"><span>{locale === "ka" ? "რეალური ნაშთი" : "Actual balance"}</span><input name="actualBalance" type="number" step="0.01" required /></label>
                <label className="field field-wide"><span>{t.date}</span><input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
                <label className="field field-full"><span>{t.note}</span><input name="note" /></label>
                <div className="field field-full items-start"><SubmitButton>{locale === "ka" ? "შეჯერება" : "Reconcile"}</SubmitButton></div>
              </form>
            </div>
          </details>

          <details className="form-details">
            <summary><Coins size={15} />{locale === "ka" ? "GEL გაცვლითი კურსი" : "GEL exchange rate"}</summary>
            <div className="details-body">
              <form action={upsertExchangeRateAction} className="form-grid">
                <label className="field"><span>{locale === "ka" ? "ვალუტა" : "Currency"}</span><input name="currency" minLength={3} maxLength={3} required /></label>
                <label className="field"><span>1 unit = GEL</span><input name="gelRate" type="number" min="0.00000001" step="0.00000001" required /></label>
                <label className="field field-wide"><span>{t.date}</span><input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
                <div className="field field-full items-start"><SubmitButton>{t.save}</SubmitButton></div>
              </form>
            </div>
          </details>
        </div>
      </div>
    </>
  );
}
