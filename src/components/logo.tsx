import logoSrc from "@/assets/logo.png";

export function Logo({ className = "size-7" }: { className?: string }) {
  return (
    <img
      src={logoSrc}
      alt="Stack'd logo"
      width={64}
      height={64}
      className={`${className} object-contain select-none`}
      draggable={false}
    />
  );
}
