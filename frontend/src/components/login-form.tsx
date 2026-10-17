"use client";

import { useActionState } from "react";
import { loginAction, type LoginState } from "@/app/actions";
import { SubmitButton } from "@/components/submit-button";

const initialState: LoginState = {};

export function LoginForm({ next }: { next?: string }) {
  const [state, action] = useActionState(loginAction, initialState);

  return (
    <form action={action} className="space-y-5">
      <input type="hidden" name="next" value={next ?? "/dashboard"} />
      <label className="field">
        <span>Username</span>
        <input name="username" autoComplete="username" required autoFocus />
      </label>
      <label className="field">
        <span>Password</span>
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
      </label>
      {state.error ? (
        <p className="status status-error" role="alert">
          {state.error}
        </p>
      ) : null}
      <SubmitButton className="button button-primary w-full">
        Sign in / შესვლა
      </SubmitButton>
    </form>
  );
}
