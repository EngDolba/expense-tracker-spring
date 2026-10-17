import { useSyncExternalStore } from "react";

/** Errors from form actions, shown above the page until dismissed. */
export type Notice = { id: number; message: string };

let notices: Notice[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

export function notifyError(message: string) {
  notices = [...notices, { id: nextId++, message }];
  emit();
}

export function dismissNotice(id: number) {
  notices = notices.filter((notice) => notice.id !== id);
  emit();
}

export function clearNotices() {
  if (!notices.length) return;
  notices = [];
  emit();
}

export function useNotices() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => notices,
  );
}
