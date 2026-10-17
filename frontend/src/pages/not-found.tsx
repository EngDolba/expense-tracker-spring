import Link from "next/link";
import { PageHeader } from "@/components/ui";
import { useDictionary } from "@/lib/i18n";

export function NotFoundPage() {
  const { locale } = useDictionary();
  return (
    <PageHeader
      eyebrow="404"
      title={locale === "ka" ? "გვერდი ვერ მოიძებნა" : "Page not found"}
      description={locale === "ka" ? "ჩანაწერი აღარ არსებობს ან ბმული არასწორია." : "The record no longer exists or the link is wrong."}
      actions={
        <Link className="button button-secondary" href="/dashboard">
          {locale === "ka" ? "მიმოხილვა" : "Overview"}
        </Link>
      }
    />
  );
}

/** Shown while a page's data loads for the first time. */
export function PageLoading() {
  return <div className="page-loading" aria-busy="true" />;
}
