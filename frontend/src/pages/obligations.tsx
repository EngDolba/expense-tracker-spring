import { Building2, FolderPlus, Plus, Trash2 } from "lucide-react";
import {
  createObligationCaseAction,
  createObligationEntryAction,
  createObligationPartyAction,
  deleteObligationEntryAction,
} from "@/app/actions";
import { BranchingLedger } from "@/components/branching-ledger";
import { CategorySelect } from "@/components/category-select";
import { SubmitButton } from "@/components/submit-button";
import { DatabaseNotice, PageHeader, StatCard } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";
import { formatMoney } from "@/lib/money";
import { useObligationPageData } from "@/lib/queries";

export default function ObligationsPage() {
  useTitle("Obligations");
  const { locale, t } = useDictionary();
  const { data, loading, unavailable } = useObligationPageData();
  if (loading) return <PageLoading />;
  const numberLocale = locale === "ka" ? "ka-GE" : "en-US";
  const outlays = data.entries
    .filter((entry) => entry.type === "OUTLAY")
    .reduce((sum, entry) => sum + entry.amount, 0);
  const reimbursements = data.entries
    .filter((entry) => entry.type === "REIMBURSEMENT")
    .reduce((sum, entry) => sum + entry.amount, 0);
  const outstanding = outlays - reimbursements;
  const categoryTotals = data.entries.reduce<
    Record<string, { amount: number; count: number }>
  >((totals, entry) => {
    if (entry.type !== "OUTLAY" || !entry.categoryId) return totals;
    const current = totals[entry.categoryId] ?? { amount: 0, count: 0 };
    current.amount += entry.amount;
    current.count += 1;
    totals[entry.categoryId] = current;
    return totals;
  }, {});

  return (
    <>
      <PageHeader
        eyebrow={locale === "ka" ? "კომპანიები და პირები" : "Companies and people"}
        title={t.obligations}
        description={
          locale === "ka"
            ? "თქვენ მიერ გაწეული ხარჯები და მიღებული ანაზღაურებები, პირადი ხარჯებისგან განცალკევებით."
            : "Track outlays and repayments without affecting personal expenses or accounts."
        }
        actions={
          <a href="#new-obligation" className="button button-primary">
            <Plus size={17} />
            {locale === "ka" ? "ახალი მოძრაობა" : "New movement"}
          </a>
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      <div className="stats-grid mt-4">
        <StatCard
          label={locale === "ka" ? "თქვენ გადაიხადეთ" : "You paid"}
          value={formatMoney(outlays, "GEL", numberLocale)}
          detail={`${data.entries.filter((entry) => entry.type === "OUTLAY").length} ${locale === "ka" ? "ჩანაწერი" : "entries"}`}
        />
        <StatCard
          label={locale === "ka" ? "დაგიბრუნდათ" : "Reimbursed"}
          value={formatMoney(reimbursements, "GEL", numberLocale)}
          tone="amber"
        />
        <StatCard
          label={locale === "ka" ? "დარჩენილი ვალდებულება" : "Outstanding"}
          value={formatMoney(outstanding, "GEL", numberLocale)}
          tone={outstanding > 0 ? "rust" : "green"}
        />
        <StatCard
          label={locale === "ka" ? "აქტიური მხარეები" : "Active parties"}
          value={String(data.parties.filter((party) => party.outstanding !== 0).length)}
          tone="blue"
          detail={`${data.parties.length} ${locale === "ka" ? "სულ" : "total"}`}
        />
      </div>

      <div className="dashboard-grid obligations-layout">
        <div className="stack">
          <section className="panel entry-composer" id="new-obligation">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ვალდებულების მოძრაობა" : "Obligation movement"}</h2>
                <p>{locale === "ka" ? "გადახდა ან ანაზღაურება" : "Outlay or reimbursement"}</p>
              </div>
            </div>
            <form action={createObligationEntryAction} className="form-grid">
              <label className="field">
                <span>{locale === "ka" ? "მხარე" : "Party"}</span>
                <select name="partyId" required defaultValue="">
                  <option value="" disabled>—</option>
                  {data.parties.map((party) => (
                    <option value={party.id} key={party.id}>{party.name}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>{locale === "ka" ? "საქმე" : "Case"}</span>
                <select name="caseId" defaultValue="">
                  <option value="">{locale === "ka" ? "— არასავალდებულო" : "— optional"}</option>
                  {data.cases.map((item) => (
                    <option value={item.id} key={item.id}>
                      {data.parties.find((party) => party.id === item.partyId)?.name} · {item.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>{locale === "ka" ? "ტიპი" : "Type"}</span>
                <select name="type" defaultValue="OUTLAY">
                  <option value="OUTLAY">{locale === "ka" ? "გადავიხადე" : "I paid"}</option>
                  <option value="REIMBURSEMENT">{locale === "ka" ? "დამიბრუნდა" : "Reimbursed"}</option>
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
              <label className="field field-wide">
                <span>{t.category}</span>
                <CategorySelect categories={data.categories} placeholder={locale === "ka" ? "კატეგორია" : "Category"} />
              </label>
              <label className="field">
                <span>{locale === "ka" ? "ტეგები" : "Tags"}</span>
                <input name="tags" />
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
                <h2>{locale === "ka" ? "ვალდებულებების რეესტრი" : "Obligation register"}</h2>
                <p>{locale === "ka" ? "ნაშთი = გადახდილი − დაბრუნებული" : "Outstanding = paid − reimbursed"}</p>
              </div>
            </div>
            {data.entries.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>{t.date}</th>
                      <th>{locale === "ka" ? "მხარე / საქმე" : "Party / case"}</th>
                      <th>{t.category}</th>
                      <th>{t.note}</th>
                      <th className="numeric">{t.amount}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {data.entries.map((entry) => (
                      <tr key={entry.id}>
                        <td>{entry.date}</td>
                        <td>
                          <strong>{entry.party.name}</strong>
                          <div className="meta">{entry.case?.name ?? "—"}</div>
                        </td>
                        <td>{entry.category?.name ?? "—"}</td>
                        <td>{entry.note || "—"}</td>
                        <td className="numeric">
                          <strong style={{ color: entry.type === "OUTLAY" ? "var(--ledger-rust)" : "var(--ledger-green)" }}>
                            {entry.type === "OUTLAY" ? "+" : "−"}
                            {formatMoney(entry.amount, "GEL", numberLocale)}
                          </strong>
                        </td>
                        <td>
                          <form action={deleteObligationEntryAction}>
                            <input type="hidden" name="id" value={entry.id} />
                            <button type="submit" className="button button-danger" aria-label="Delete">
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
                <h2>{locale === "ka" ? "მხარეების ნაშთები" : "Party balances"}</h2>
                <p>{locale === "ka" ? "დადებითი თანხა თქვენ გეკუთვნით" : "Positive means owed to you"}</p>
              </div>
            </div>
            <div className="grid gap-2">
              {data.parties.map((party) => (
                <div className="flex items-center gap-3 border-b border-black/5 py-3" key={party.id}>
                  <span className="user-avatar"><Building2 size={15} /></span>
                  <div className="min-w-0 flex-1">
                    <strong>{party.name}</strong>
                    <div className="meta">{party.note || (locale === "ka" ? "მუდმივი ჟურნალი" : "Ongoing ledger")}</div>
                  </div>
                  <strong className="money" style={{ color: party.outstanding > 0 ? "var(--ledger-rust)" : "var(--ledger-green)" }}>
                    {formatMoney(party.outstanding, "GEL", numberLocale)}
                  </strong>
                </div>
              ))}
              {!data.parties.length ? <div className="empty-state">{t.noData}</div> : null}
            </div>
            <div className="mt-4 grid gap-2">
              <details className="form-details">
                <summary><Building2 size={15} />{locale === "ka" ? "მხარის დამატება" : "Add party"}</summary>
                <div className="details-body">
                  <form action={createObligationPartyAction} className="form-grid">
                    <label className="field field-wide"><span>{locale === "ka" ? "სახელი" : "Name"}</span><input name="name" required /></label>
                    <label className="field field-wide"><span>{t.note}</span><input name="note" /></label>
                    <div className="field field-full items-start"><SubmitButton>{t.add}</SubmitButton></div>
                  </form>
                </div>
              </details>
              <details className="form-details">
                <summary><FolderPlus size={15} />{locale === "ka" ? "საქმის დამატება" : "Add case"}</summary>
                <div className="details-body">
                  <form action={createObligationCaseAction} className="form-grid">
                    <label className="field field-wide">
                      <span>{locale === "ka" ? "მხარე" : "Party"}</span>
                      <select name="partyId" required defaultValue="">
                        <option value="" disabled>—</option>
                        {data.parties.map((party) => <option value={party.id} key={party.id}>{party.name}</option>)}
                      </select>
                    </label>
                    <label className="field field-wide"><span>{locale === "ka" ? "საქმის სახელი" : "Case name"}</span><input name="name" required /></label>
                    <label className="field field-full"><span>{t.note}</span><input name="note" /></label>
                    <div className="field field-full items-start"><SubmitButton>{t.add}</SubmitButton></div>
                  </form>
                </div>
              </details>
            </div>
          </section>
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ხარჯების კატეგორიები" : "Outlay categories"}</h2>
                <p>{locale === "ka" ? "მხოლოდ ვალდებულებების ანალიზი" : "Obligations analysis only"}</p>
              </div>
            </div>
            <BranchingLedger
              categories={data.categories}
              totals={categoryTotals}
              storageKey="obligation-category-tree"
              locale={numberLocale}
              emptyLabel={t.noData}
            />
          </section>
        </div>
      </div>
    </>
  );
}
