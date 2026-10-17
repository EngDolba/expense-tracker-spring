import { BookOpenText } from "lucide-react";
import { Navigate, useSearchParams } from "react-router";
import { safeNext } from "@/app/actions";
import { LoginForm } from "@/components/login-form";
import { useTitle } from "@/lib/use-title";
import { useSession } from "@/pages/protected-layout";

export default function LoginPage() {
  useTitle("Sign in");
  const [searchParams] = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const session = useSession();
  if (session.data) return <Navigate to={next} replace />;

  return (
    <main className="login-page">
      <section className="login-card">
        <span className="brand-mark">
          <BookOpenText size={21} />
        </span>
        <h1>Financial Ledger</h1>
        <p>
          Your private expense, obligation, and account registers.
          <br />
          პირადი ფინანსური ჟურნალი.
        </p>
        <LoginForm next={next} />
      </section>
    </main>
  );
}
