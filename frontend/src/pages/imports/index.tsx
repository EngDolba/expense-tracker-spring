import Link from "next/link";
import { FileSpreadsheet, ShieldCheck, Upload } from "lucide-react";
import { ActivitySpine, DatabaseNotice, PageHeader, WarningNotice } from "@/components/ui";
import { useSearchParams } from "react-router";
import { uploadWorkbookAction } from "@/app/actions";
import { useDictionary } from "@/lib/i18n";
import { useImportBatches } from "@/lib/queries";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";

export default function ImportsPage() {
  useTitle("Import");
  const { locale, t } = useDictionary();
  const [searchParams] = useSearchParams();
  const params = { error: searchParams.get("error") ?? undefined };
  const { data: batches, loading, unavailable } = useImportBatches();
  if (loading) return <PageLoading />;
  const stagedCount = batches.filter((batch) => batch.status === "STAGED").length;
  const committedCount = batches.filter((batch) => batch.status === "COMMITTED").length;

  return (
    <>
      <PageHeader
        eyebrow="XLDR · XLSX · XLSM"
        title={t.imports}
        description={
          locale === "ka"
            ? "ფაილი მუშავდება სერვერზე, მაკროები არასოდეს სრულდება და ფინანსური მონაცემები Git-ში არ ხვდება."
            : "Files are processed server-side, macros never execute, and financial rows never enter Git."
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      {params.error ? <div className="mt-4"><WarningNotice>{params.error}</WarningNotice></div> : null}
      <section className="import-spine" aria-label={locale === "ka" ? "იმპორტის დინება" : "Import workflow"}>
        <ActivitySpine
          label={locale === "ka" ? "ეტაპი 01" : "Stage 01"}
          value={locale === "ka" ? "ატვირთვა" : "Upload"}
          detail={locale === "ka" ? "სამუშაო წიგნი" : "Workbook intake"}
          tone="blue"
        />
        <ActivitySpine
          label={locale === "ka" ? "ეტაპი 02" : "Stage 02"}
          value={String(stagedCount)}
          detail={locale === "ka" ? "გადახედვას ელოდება" : "Awaiting review"}
          tone="amber"
        />
        <ActivitySpine
          label={locale === "ka" ? "ეტაპი 03" : "Stage 03"}
          value={String(committedCount)}
          detail={locale === "ka" ? "დადასტურებული პარტიები" : "Committed batches"}
        />
      </section>
      <div className="dashboard-grid import-layout mt-4">
        <section className="panel import-stage">
          <div className="panel-header">
            <div>
              <h2>{locale === "ka" ? "სამუშაო წიგნის ატვირთვა" : "Upload workbook"}</h2>
              <p>{locale === "ka" ? "მაქსიმუმ 10 MB · .xlsx ან .xlsm" : "Maximum 10 MB · .xlsx or .xlsm"}</p>
            </div>
            <Upload size={20} />
          </div>
          <form action={uploadWorkbookAction}>
            <label className="field field-full">
              <span>{locale === "ka" ? "Excel ფაილი" : "Excel file"}</span>
              <input name="workbook" type="file" accept=".xlsx,.xlsm" required />
            </label>
            <div className="mt-4">
              <button className="button button-primary" type="submit">
                <FileSpreadsheet size={17} />
                {locale === "ka" ? "წაკითხვა და გადახედვა" : "Parse and preview"}
              </button>
            </div>
          </form>
          <div className="status status-success mt-5">
            <ShieldCheck size={18} />
            <span>
              {locale === "ka"
                ? "იმპორტი ჯერ ინახება დროებით ეტაპად. მონაცემები მთავარ ჟურნალში მხოლოდ თქვენი დადასტურების შემდეგ შევა."
                : "Imports are staged first. Nothing enters the main ledger until you confirm the preview."}
            </span>
          </div>
        </section>
        <section className="panel import-history">
          <div className="panel-header">
            <div>
              <h2>{locale === "ka" ? "იმპორტის ისტორია" : "Import history"}</h2>
              <p>{locale === "ka" ? "SHA-256 იცავს ერთი ფაილის ხელმეორედ ატვირთვისგან" : "SHA-256 prevents accidental duplicate uploads"}</p>
            </div>
          </div>
          {batches.length ? (
            <div className="grid gap-2">
              {batches.map((batch) => (
                <Link
                  className="flex items-center gap-3 rounded-lg border border-black/5 p-3 hover:bg-[var(--ledger-green-soft)]"
                  href={`/imports/${batch.id}`}
                  key={batch.id}
                >
                  <FileSpreadsheet size={18} />
                  <div className="min-w-0 flex-1">
                    <strong className="block truncate">{batch.fileName}</strong>
                    <span className="meta">
                      {batch.totalRows} {locale === "ka" ? "სტრიქონი" : "rows"} · {batch.status}
                    </span>
                  </div>
                  <span className={batch.status === "COMMITTED" ? "tag" : "tag tag-amber"}>
                    {batch.status}
                  </span>
                </Link>
              ))}
            </div>
          ) : (
            <div className="empty-state">{t.noData}</div>
          )}
        </section>
      </div>
    </>
  );
}
