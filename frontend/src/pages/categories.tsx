import { Archive, FolderTree, Save } from "lucide-react";
import {
  archiveCategoryAction,
  createCategoryAction,
  updateCategoryAction,
} from "@/app/actions";
import { CategorySelect } from "@/components/category-select";
import { SubmitButton } from "@/components/submit-button";
import { DatabaseNotice, PageHeader } from "@/components/ui";
import { buildCategoryTree, type CategoryTreeNode } from "@/lib/categories";
import type { Category } from "@/lib/api-types";
import { useDictionary } from "@/lib/i18n";
import { useCategoriesPageData } from "@/lib/queries";
import { useTitle } from "@/lib/use-title";
import { PageLoading } from "@/pages/not-found";

function CategoryEditor({
  nodes,
  categories,
  locale,
  depth = 0,
}: {
  nodes: CategoryTreeNode[];
  categories: Category[];
  locale: "ka" | "en";
  depth?: number;
}) {
  return nodes.map((node) => (
    <div key={node.id}>
      <div
        className="category-editor-row border-b border-black/5 py-3"
        style={{ paddingLeft: depth * 18 }}
      >
        <form action={updateCategoryAction} className="flex flex-wrap items-end gap-2">
          <input type="hidden" name="id" value={node.id} />
          <label className="field min-w-[180px] flex-1">
            <span>{locale === "ka" ? "სახელი" : "Name"}</span>
            <input name="name" defaultValue={node.name} required />
          </label>
          <label className="field min-w-[210px] flex-1">
            <span>{locale === "ka" ? "მშობელი" : "Parent"}</span>
            <CategorySelect
              categories={categories.filter((category) => category.id !== node.id)}
              name="parentId"
              placeholder={locale === "ka" ? "ძირეული კატეგორია" : "Root category"}
              defaultValue={node.parentId}
            />
          </label>
          <SubmitButton className="button button-secondary" pendingText="…">
            <Save size={15} />
            {locale === "ka" ? "შენახვა" : "Save"}
          </SubmitButton>
        </form>
        <form action={archiveCategoryAction} className="mt-2">
          <input type="hidden" name="id" value={node.id} />
          <button className="button button-danger" type="submit">
            <Archive size={14} />
            {locale === "ka" ? "არქივში" : "Archive"}
          </button>
        </form>
      </div>
      <CategoryEditor
        nodes={node.children}
        categories={categories}
        locale={locale}
        depth={depth + 1}
      />
    </div>
  ));
}

export default function CategoriesPage() {
  useTitle("Categories");
  const { locale, t } = useDictionary();
  const { data, loading, unavailable } = useCategoriesPageData();
  if (loading) return <PageLoading />;
  const { categories, codes } = data;
  const tree = buildCategoryTree(categories);

  return (
    <>
      <PageHeader
        eyebrow={locale === "ka" ? "საერთო ლექსიკონი" : "Shared dictionary"}
        title={t.categories}
        description={
          locale === "ka"
            ? "ერთი ულიმიტო სიღრმის ხე გამოიყენება ხარჯებისა და ვალდებულებების კლასიფიკაციისთვის."
            : "One unlimited-depth tree classifies expenses and obligations."
        }
      />
      {unavailable ? <DatabaseNotice /> : null}
      <div className="dashboard-grid category-layout mt-4">
        <div className="stack">
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "კატეგორიების ხე" : "Category tree"}</h2>
                <p>{locale === "ka" ? "გადაარქვით, გადაიტანეთ ან დაარქივეთ ნებისმიერი დონე" : "Rename, move, or archive any level"}</p>
              </div>
            </div>
            {tree.length ? (
              <CategoryEditor nodes={tree} categories={categories} locale={locale} />
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
          </section>
        </div>
        <div className="stack">
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "ახალი კატეგორია" : "New category"}</h2>
                <p>{locale === "ka" ? "მშობელი არჩევითია" : "Parent is optional"}</p>
              </div>
              <FolderTree size={19} />
            </div>
            <form action={createCategoryAction} className="form-grid">
              <label className="field field-full">
                <span>{locale === "ka" ? "სახელი" : "Name"}</span>
                <input name="name" required />
              </label>
              <label className="field field-full">
                <span>{locale === "ka" ? "მშობელი" : "Parent"}</span>
                <CategorySelect
                  categories={categories}
                  name="parentId"
                  placeholder={locale === "ka" ? "ძირეული კატეგორია" : "Root category"}
                />
              </label>
              <div className="field field-full items-start">
                <SubmitButton>{t.add}</SubmitButton>
              </div>
            </form>
          </section>
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2>{locale === "ka" ? "Excel კოდები" : "Excel codes"}</h2>
                <p>{locale === "ka" ? "XLDR ლექსიკონიდან" : "From the XLDR dictionary"}</p>
              </div>
            </div>
            {codes.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead><tr><th>{locale === "ka" ? "კოდი" : "Code"}</th><th>{t.category}</th></tr></thead>
                  <tbody>
                    {codes.map((code) => (
                      <tr key={code.id}><td className="money">{code.code}</td><td>{code.category.name}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-state">{t.noData}</div>
            )}
          </section>
        </div>
      </div>
    </>
  );
}
