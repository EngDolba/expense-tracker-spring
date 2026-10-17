import type { NavigateOptions } from "react-router";

/**
 * Navigation for code outside components (actions). The router registers
 * itself on start-up; until then this is a no-op.
 */
let navigator: (to: string, options?: NavigateOptions) => void = () => {};

export function setNavigator(next: (to: string, options?: NavigateOptions) => void) {
  navigator = next;
}

export function navigate(to: string, options?: NavigateOptions) {
  navigator(to, options);
}
