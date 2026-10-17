"use client";

import { useState } from "react";

export type NumberBounds = { min: number; max: number; integer?: boolean };

export const WEIGHT_BOUNDS: NumberBounds = { min: 0, max: 2_000 };
export const REPS_BOUNDS: NumberBounds = { min: 1, max: 200, integer: true };
export const SET_COUNT_BOUNDS: NumberBounds = { min: 1, max: 20, integer: true };

export function clampNumber(
  value: number,
  { min, max, integer }: NumberBounds,
) {
  const bounded = Math.min(max, Math.max(min, value));
  return integer ? Math.round(bounded) : bounded;
}

/**
 * A number input that behaves like a text field while it is being typed in.
 * The field owns the raw text, so it can sit empty mid-edit; only a value that
 * actually parses is handed up, and blurring an empty field snaps it back to
 * the last good number. The parent therefore never holds a blank, which is why
 * nothing downstream has to guard against one.
 *
 * Deliberately not paired with a disabled submit button: a button disabled
 * while the field is empty would swallow the very click that blurs the field
 * and restores it.
 */
export function NumberField({
  label,
  value,
  bounds,
  step,
  onCommit,
}: {
  label: string;
  value: number;
  bounds: NumberBounds;
  step?: string;
  onCommit: (value: number) => void;
}) {
  const [text, setText] = useState(() => String(value));

  function handleChange(raw: string) {
    setText(raw);
    if (raw.trim() === "") return;
    const numeric = Number(raw);
    // Leave the text alone so partial input like "6." survives the keystroke.
    if (Number.isFinite(numeric)) onCommit(clampNumber(numeric, bounds));
  }

  function handleBlur() {
    const numeric = Number(text);
    if (text.trim() === "" || !Number.isFinite(numeric)) {
      setText(String(value));
      return;
    }
    const clamped = clampNumber(numeric, bounds);
    setText(String(clamped));
    onCommit(clamped);
  }

  return (
    <label>
      <span>{label}</span>
      <input
        type="number"
        inputMode={bounds.integer ? "numeric" : "decimal"}
        min={bounds.min}
        max={bounds.max}
        step={step}
        value={text}
        onChange={(event) => handleChange(event.target.value)}
        onBlur={handleBlur}
      />
    </label>
  );
}
