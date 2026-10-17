import Link from "next/link";
import { CheckCircle2, FileWarning, RotateCcw } from "lucide-react";
import { useParams, useSearchParams } from "react-router";
import { commitImportAction } from "@/app/actions";
import { SubmitButton } from "@/components/submit-button";
import { PageHeader, StatCard, WarningNotice } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";
import { formatMoney } from "@/lib/money";
import { useImportDetail } from "@/lib/queries";
import { useTitle } from "@/lib/use-title";
import { NotFoundPage, PageLoading } from "@/pages/not-found";

export default function ImportPreviewPage() {
  useTitle("Import preview");
  const { id = "" } = useParams();
  const [searchParams] = useSearchParams();
  const query = {
    duplicate: searchParams.get("duplicate") ?? undefined,
    committed: searchParams.get("committed") ?? undefined,
  };
  const { locale } = useDictionary();
  const { data: detail, loading } = useImportDetail(id);
  if (loading) return <PageLoading />;
  if (!detail) return <NotFoundPage />;

  const batch = { ...detail.batch, rows: detail.rows };
  const rows = detail.rows;
  const amountTotal = rows.reduce(
    (sum, row) =>
      sum +
      (row.normalized.type === "EXPENSE"
        ? -row.normalized.amount
        : row.normalized.amount),
    0,
  );
  const numberLocale = locale === "ka" ? "ka-GE" : "en-US";

  return (
    <>
      <PageHeader
        eyebrow={batch.status}
        title={batch.fileName}
        description={`${batch.sourceSheet ?? "—"} · SHA-256 ${batch.fileHash.slice(0, 12)}…`}
        actions={
          <Link className="button button-secondary" href="/imports">
            <RotateCcw size={16} />
            {locale === "ka" ? "იმპორტები" : "Imports"}
          </Link>
        }
      />
      {query.duplicate ? (
        <div className="mb-4">
          <WarningNotice>
            {locale === "ka"
              ? "ეს ზუსტად იგივე ფაილი უკვე ატვირთულია."
              : "This exact file has already been uploaded."}
          </WarningNotice>
        </div>
      ) : null}
      {query.committed || batch.status === "COMMITTED" ? (
        <div className="status status-success mb-4">
          <CheckCircle2 size={18} />
          <span>{locale === "ka" ? "იმპორტი დასრულებულია." : "Import committed successfully."}</span>
        </div>
      ) : null}
      <div className="stats-grid">
        <StatCard label={locale === "ka" ? "სულ" : "Total rows"} value={String(batch.totalRows)} />
        <StatCard label={locale === "ka" ? "ხარჯი" : "Expenses"} value={String(batch.expenseRows)} detail={locale === "ka" ? "უარყოფითი XLDR თანხები" : "Negative XLDR amounts"} />
        <StatCard label={locale === "ka" ? "შემოსავალი" : "Income"} value={String(batch.incomeRows)} tone="amber" detail={locale === "ka" ? "დადებითი XLDR თანხები" : "Positive XLDR amounts"} />
        <StatCard label={locale === "ka" ? "შეცდომა" : "Errors"} value={String(batch.errorRows)} tone={batch.errorRows ? "rust" : "green"} detail={`${batch.mappingCount} mappings`} />
      </div>
      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>{locale === "ka" ? "იმპორტის გადახედვა" : "Import preview"}</h2>
            <p>
              {locale === "ka" ? "ხელმოწერილი ჯამი" : "Signed total"}:{" "}
              {formatMoney(amountTotal, "GEL", numberLocale)}
            </p>
          </div>
          {batch.status === "STAGED" && batch.errorRows === 0 ? (
            <form action={commitImportAction}>
              <input type="hidden" name="batchId" value={batch.id} />
              <SubmitButton>
                <CheckCircle2 size={17} />
                {locale === "ka"
                  ? `${batch.totalRows} ჩანაწერის დადასტურება`
                  : `Commit ${batch.totalRows} rows`}
              </SubmitButton>
            </form>
          ) : null}
        </div>
        {batch.errorRows ? (
          <div className="status status-error mb-4">
            <FileWarning size={18} />
            <span>
              {locale === "ka"
                ? "შეცდომიანი სტრიქონების გამო იმპორტის დადასტურება დაბლოკილია."
                : "Commit is blocked until invalid rows are corrected in the workbook."}
            </span>
          </div>
        ) : null}
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>{locale === "ka" ? "თარიღი" : "Date"}</th>
                <th>{locale === "ka" ? "ტიპი" : "Type"}</th>
                <th>{locale === "ka" ? "კატეგორია" : "Category"}</th>
                <th>{locale === "ka" ? "მიმღები" : "Recipient"}</th>
                <th>{locale === "ka" ? "შენიშვნა" : "Note"}</th>
                <th className="numeric">{locale === "ka" ? "თანხა" : "Amount"}</th>
                <th>{locale === "ka" ? "სტატუსი" : "Status"}</th>
              </tr>
            </thead>
            <tbody>
              {rows.slice(0, 500).map((row) => (
                <tr key={row.id}>
                  <td className="money">{row.rowNumber}</td>
                  <td>{row.normalized.date || "—"}</td>
                  <td>{row.normalized.type}</td>
                  <td>{row.normalized.categoryPath.join(" → ") || "—"}</td>
                  <td>{row.normalized.recipient || "—"}</td>
                  <td>{row.normalized.note || "—"}</td>
                  <td className="numeric">{formatMoney(row.normalized.amount, "GEL", numberLocale)}</td>
                  <td>
                    {row.errors.length ? (
                      <span className="tag tag-amber" title={row.errors.join(" ")}>
                        {row.errors.length} error
                      </span>
                    ) : (
                      <span className="tag">OK</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
