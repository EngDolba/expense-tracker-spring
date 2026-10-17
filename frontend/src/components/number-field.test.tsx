// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  clampNumber,
  NumberField,
  REPS_BOUNDS,
  WEIGHT_BOUNDS,
} from "@/components/number-field";

afterEach(cleanup);

function renderField(props: Partial<Parameters<typeof NumberField>[0]> = {}) {
  const onCommit = vi.fn();
  const label = props.label ?? "Weight (kg)";
  render(
    <NumberField
      label={label}
      value={60}
      bounds={WEIGHT_BOUNDS}
      step="0.25"
      onCommit={onCommit}
      {...props}
    />,
  );
  return { onCommit, input: screen.getByLabelText(label) };
}

describe("clampNumber", () => {
  it("holds a value inside its bounds and rounds integer fields", () => {
    expect(clampNumber(2_500, WEIGHT_BOUNDS)).toBe(2_000);
    expect(clampNumber(-5, WEIGHT_BOUNDS)).toBe(0);
    expect(clampNumber(62.5, WEIGHT_BOUNDS)).toBe(62.5);
    expect(clampNumber(7.4, REPS_BOUNDS)).toBe(7);
    expect(clampNumber(0, REPS_BOUNDS)).toBe(1);
  });
});

describe("NumberField", () => {
  it("can be cleared completely instead of snapping to a minimum", async () => {
    const user = userEvent.setup();
    const { input } = renderField();

    await user.clear(input);

    expect(input).toHaveValue(null);
  });

  it("keeps a reps field empty rather than forcing it back to 1", async () => {
    const user = userEvent.setup();
    const { input } = renderField({
      label: "Reps",
      value: 8,
      bounds: REPS_BOUNDS,
      step: undefined,
    });

    await user.clear(input);

    expect(input).toHaveValue(null);
  });

  it("types a fresh value into an emptied field without a stuck digit", async () => {
    const user = userEvent.setup();
    const { input, onCommit } = renderField();

    await user.clear(input);
    await user.type(input, "72.5");

    expect(input).toHaveValue(72.5);
    expect(onCommit).toHaveBeenLastCalledWith(72.5);
  });

  it("does not commit anything while the field sits empty", async () => {
    const user = userEvent.setup();
    const { input, onCommit } = renderField();

    await user.clear(input);

    expect(onCommit).not.toHaveBeenCalled();
  });

  it("restores the last good value when an empty field is blurred", async () => {
    const user = userEvent.setup();
    const { input } = renderField();

    await user.clear(input);
    await user.tab();

    expect(input).toHaveValue(60);
  });

  it("normalizes an out-of-range entry on blur", async () => {
    const user = userEvent.setup();
    const { input, onCommit } = renderField();

    await user.clear(input);
    await user.type(input, "5000");
    await user.tab();

    expect(input).toHaveValue(2_000);
    expect(onCommit).toHaveBeenLastCalledWith(2_000);
  });

  it("rounds a decimal reps entry on blur", async () => {
    const user = userEvent.setup();
    const onCommit = vi.fn();
    render(
      <NumberField
        label="Reps"
        value={8}
        bounds={REPS_BOUNDS}
        onCommit={onCommit}
      />,
    );
    const input = screen.getByLabelText("Reps");

    await user.clear(input);
    await user.type(input, "7.6");
    await user.tab();

    expect(input).toHaveValue(8);
    expect(onCommit).toHaveBeenLastCalledWith(8);
  });
});
