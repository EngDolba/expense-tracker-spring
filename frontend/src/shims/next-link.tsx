import type { ComponentProps } from "react";
import { Link as RouterLink } from "react-router";

type Props = Omit<ComponentProps<typeof RouterLink>, "to"> & { href: string };

/** next/link on top of React Router: `href` becomes `to`. */
export default function Link({ href, ...props }: Props) {
  return <RouterLink to={href} {...props} />;
}
