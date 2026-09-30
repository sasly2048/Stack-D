import * as React from "react";

import { cn } from "@/lib/utils";
import { FOCUS_RING } from "@/components/ui/interactive";

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          `flex h-[var(--control-height)] min-h-11 w-full rounded-md border border-input bg-transparent px-3 text-base leading-normal shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground aria-invalid:border-destructive/60 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm ${FOCUS_RING}`,
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Input.displayName = "Input";

export { Input };
