import * as React from "react";

import { cn } from "@/lib/utils";
import { FOCUS_RING } from "@/components/ui/interactive";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        className={cn(
          `flex min-h-24 w-full resize-y rounded-md border border-input bg-transparent px-3 py-2.5 text-base shadow-sm placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50 md:text-sm ${FOCUS_RING}`,
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
