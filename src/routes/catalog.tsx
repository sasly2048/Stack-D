import { createFileRoute } from "@tanstack/react-router";
import { useState, type ReactNode } from "react";
import { Nav } from "@/components/nav";
import { siteUrl } from "@/lib/site";

// shadcn/ui
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Checkbox } from "@/components/ui/checkbox";
import { Switch } from "@/components/ui/switch";
import { Slider } from "@/components/ui/slider";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Toaster } from "@/components/ui/sonner";
import { toast } from "sonner";

// FX
import { MatrixText } from "@/components/fx/matrix-text";
import { ShinyText } from "@/components/fx/shiny-text";
import { Particles } from "@/components/fx/particles";
import { Marquee } from "@/components/fx/marquee";
import { OrbitingCircles } from "@/components/fx/orbiting-circles";
import { NumberTicker } from "@/components/fx/number-ticker";
import { TextReveal } from "@/components/fx/text-reveal";
import { Meteors } from "@/components/fx/meteors";
import { LightRays } from "@/components/fx/light-rays";
import { Ripple } from "@/components/fx/ripple";
import { ScrollVelocity } from "@/components/fx/scroll-velocity";
import { MapSkeleton, MeteorSkeleton } from "@/components/fx/skeleton";

export const Route = createFileRoute("/catalog")({
  component: CatalogPage,
  head: () => ({
    meta: [
      { title: "Component Catalog — Stack'd" },
      {
        name: "description",
        content:
          "Browsable inventory of every shadcn/ui primitive and custom FX component used in Stack'd, with live examples and copy-ready snippets.",
      },
      { property: "og:title", content: "Stack'd Component Catalog" },
      {
        property: "og:description",
        content: "Every UI primitive and motion effect in Stack'd, live and copy-ready.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
      { property: "og:url", content: siteUrl("/catalog") },
    ],
    links: [{ rel: "canonical", href: siteUrl("/catalog") }],
  }),
});

/* ---------- section primitives ---------- */

function Demo({
  name,
  description,
  code,
  children,
}: {
  name: string;
  description: string;
  code: string;
  children: ReactNode;
}) {
  const [tab, setTab] = useState<"preview" | "code">("preview");
  return (
    <Card className="overflow-hidden border-white/10 bg-black/40 backdrop-blur">
      <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
        <div>
          <CardTitle className="font-mono text-sm text-white">{name}</CardTitle>
          <CardDescription className="text-xs text-white/60">{description}</CardDescription>
        </div>
        <div className="flex gap-1 rounded-md border border-white/10 bg-black/60 p-0.5">
          {/* aria-pressed carries the selected state: it was previously
              signalled by background colour alone, which a screen reader can't
              convey. The inactive label also moves from white/50 (~3.2:1) to
              white/70 so 10px text clears AA. */}
          <button
            type="button"
            aria-pressed={tab === "preview"}
            onClick={() => setTab("preview")}
            className={`cursor-pointer rounded px-2 py-0.5 text-[10px] uppercase tracking-wider transition ${
              tab === "preview" ? "bg-white/10 text-white" : "text-white/70 hover:text-white"
            }`}
          >
            Preview
          </button>
          <button
            type="button"
            aria-pressed={tab === "code"}
            onClick={() => setTab("code")}
            className={`cursor-pointer rounded px-2 py-0.5 text-[10px] uppercase tracking-wider transition ${
              tab === "code" ? "bg-white/10 text-white" : "text-white/70 hover:text-white"
            }`}
          >
            Code
          </button>
        </div>
      </CardHeader>
      <CardContent>
        {tab === "preview" ? (
          <div className="flex min-h-[140px] items-center justify-center rounded-md border border-white/5 bg-gradient-to-br from-white/[0.02] to-transparent p-6">
            {children}
          </div>
        ) : (
          <pre className="max-h-[280px] overflow-auto rounded-md border border-white/5 bg-black/70 p-4 text-[11px] leading-relaxed text-white/80">
            <code>{code.trim()}</code>
          </pre>
        )}
      </CardContent>
    </Card>
  );
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-24">
      <div className="mb-6 flex items-baseline justify-between border-b border-white/10 pb-3">
        <h2 className="text-2xl font-semibold text-white">{title}</h2>
        <a
          href={`#${id}`}
          className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground hover:text-primary"
        >
          #{id}
        </a>
      </div>
      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">{children}</div>
    </section>
  );
}

/* ---------- page ---------- */

function CatalogPage() {
  const [switchOn, setSwitchOn] = useState(true);
  const [sliderVal, setSliderVal] = useState([42]);
  const [progress] = useState(66);
  const [query, setQuery] = useState("");

  const groups = [
    { id: "buttons", label: "Buttons & Actions" },
    { id: "inputs", label: "Inputs & Forms" },
    { id: "display", label: "Display & Data" },
    { id: "overlays", label: "Overlays" },
    { id: "navigation", label: "Navigation" },
    { id: "feedback", label: "Feedback" },
    { id: "fx-text", label: "FX — Text" },
    { id: "fx-motion", label: "FX — Motion & Ambient" },
    { id: "fx-skeleton", label: "FX — Skeletons" },
  ];

  return (
    <div className="min-h-screen overflow-x-hidden bg-obsidian text-white">
      <Nav />
      <Toaster />

      <header className="mx-auto max-w-6xl px-6 pb-8 pt-24">
        <p className="font-mono text-[10px] uppercase tracking-[0.3em] text-primary">
          Component Catalog
        </p>
        <h1 className="mt-3 text-4xl font-semibold tracking-tight md:text-5xl">
          Every primitive, one page.
        </h1>
        <p className="mt-3 max-w-2xl text-white/60">
          Live examples for every shadcn/ui component and custom FX in Stack'd. Toggle{" "}
          <span className="font-mono text-white/80">Code</span> on any card to copy usage.
        </p>
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter sections…"
          className="mt-6 max-w-sm bg-black/40"
        />
      </header>

      {/* jump nav */}
      <nav className="sticky top-16 z-30 border-y border-white/10 bg-obsidian/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl gap-4 overflow-x-auto px-6 py-3 text-xs">
          {groups
            .filter((g) => !query || g.label.toLowerCase().includes(query.toLowerCase()))
            .map((g) => (
              <a
                key={g.id}
                href={`#${g.id}`}
                className="whitespace-nowrap text-white/60 hover:text-primary"
              >
                {g.label}
              </a>
            ))}
        </div>
      </nav>

      <main className="mx-auto max-w-6xl space-y-16 px-6 py-12">
        {/* Buttons */}
        <Section id="buttons" title="Buttons & Actions">
          <Demo
            name="Button — variants"
            description="Default, secondary, outline, ghost, destructive."
            code={`<Button>Default</Button>
<Button variant="secondary">Secondary</Button>
<Button variant="outline">Outline</Button>
<Button variant="ghost">Ghost</Button>
<Button variant="destructive">Delete</Button>`}
          >
            <div className="flex flex-wrap justify-center gap-2">
              <Button>Default</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="outline">Outline</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="destructive">Delete</Button>
            </div>
          </Demo>

          <Demo
            name="Button — sizes"
            description="sm · default · lg · icon"
            code={`<Button size="sm">Small</Button>
<Button>Default</Button>
<Button size="lg">Large</Button>`}
          >
            <div className="flex flex-wrap items-center justify-center gap-2">
              <Button size="sm">Small</Button>
              <Button>Default</Button>
              <Button size="lg">Large</Button>
            </div>
          </Demo>

          <Demo
            name="Ember sweep CTA"
            description="Custom class btn-ember with gradient hover-sweep."
            code={`<button className="btn-ember px-6 py-3 rounded-md">Enter</button>`}
          >
            <button className="btn-ember rounded-md px-6 py-3 font-medium">Enter</button>
          </Demo>
        </Section>

        {/* Inputs */}
        <Section id="inputs" title="Inputs & Forms">
          <Demo
            name="Input"
            description="Text input with label."
            code={`<Label htmlFor="email">Email</Label>
<Input id="email" type="email" placeholder="you@stackd.com" />`}
          >
            <div className="w-full max-w-xs space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@stackd.com" />
            </div>
          </Demo>

          <Demo
            name="Textarea"
            description="Multi-line input."
            code={`<Textarea placeholder="Session notes…" rows={4} />`}
          >
            <Textarea placeholder="Session notes…" rows={4} className="w-full max-w-xs" />
          </Demo>

          <Demo
            name="Checkbox"
            description=""
            code={`<Checkbox id="c1" defaultChecked />
<Label htmlFor="c1">Focus mode</Label>`}
          >
            <div className="flex items-center gap-2">
              <Checkbox id="c1" defaultChecked />
              <Label htmlFor="c1">Focus mode</Label>
            </div>
          </Demo>

          <Demo
            name="Switch"
            description="Controlled toggle."
            code={`<Switch checked={on} onCheckedChange={setOn} />`}
          >
            <Switch checked={switchOn} onCheckedChange={setSwitchOn} aria-label="Demo toggle" />
          </Demo>

          <Demo
            name="Slider"
            description="Value: adjustable."
            code={`<Slider value={val} onValueChange={setVal} max={100} />`}
          >
            <div className="w-full max-w-xs space-y-2">
              <Slider
                value={sliderVal}
                onValueChange={setSliderVal}
                max={100}
                aria-label="Demo value"
              />
              <p className="text-center font-mono text-xs text-white/60">{sliderVal[0]}</p>
            </div>
          </Demo>

          <Demo
            name="Radio group"
            description=""
            code={`<RadioGroup defaultValue="a">
  <RadioGroupItem value="a" id="a" /> <Label htmlFor="a">A</Label>
  <RadioGroupItem value="b" id="b" /> <Label htmlFor="b">B</Label>
</RadioGroup>`}
          >
            <RadioGroup defaultValue="a" className="flex gap-4">
              <div className="flex items-center gap-2">
                <RadioGroupItem value="a" id="ra" />
                <Label htmlFor="ra">Deep</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="b" id="rb" />
                <Label htmlFor="rb">Light</Label>
              </div>
            </RadioGroup>
          </Demo>

          <Demo
            name="Select"
            description=""
            code={`<Select>
  <SelectTrigger><SelectValue placeholder="Pick" /></SelectTrigger>
  <SelectContent><SelectItem value="a">A</SelectItem></SelectContent>
</Select>`}
          >
            <Select>
              <SelectTrigger className="w-40" aria-label="Room type">
                <SelectValue placeholder="Room type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="solo">Solo</SelectItem>
                <SelectItem value="pair">Pair</SelectItem>
                <SelectItem value="group">Group</SelectItem>
              </SelectContent>
            </Select>
          </Demo>
        </Section>

        {/* Display */}
        <Section id="display" title="Display & Data">
          <Demo
            name="Badge"
            description="Status pill."
            code={`<Badge>Live</Badge>
<Badge variant="secondary">Beta</Badge>
<Badge variant="outline">v2</Badge>`}
          >
            <div className="flex gap-2">
              <Badge>Live</Badge>
              <Badge variant="secondary">Beta</Badge>
              <Badge variant="outline">v2</Badge>
            </div>
          </Demo>

          <Demo
            name="Avatar"
            description="Fallback initials."
            code={`<Avatar><AvatarFallback>SD</AvatarFallback></Avatar>`}
          >
            <div className="flex gap-2">
              <Avatar>
                <AvatarFallback>SD</AvatarFallback>
              </Avatar>
              <Avatar>
                <AvatarFallback className="bg-primary/20 text-primary">JR</AvatarFallback>
              </Avatar>
            </div>
          </Demo>

          <Demo
            name="Card"
            description="Container primitive."
            code={`<Card><CardHeader><CardTitle>Title</CardTitle></CardHeader>
<CardContent>Body</CardContent></Card>`}
          >
            <Card className="w-full max-w-xs">
              <CardHeader>
                <CardTitle>Session</CardTitle>
                <CardDescription>25 min · Deep</CardDescription>
              </CardHeader>
              <CardContent className="text-sm text-white/70">Focus block completed.</CardContent>
            </Card>
          </Demo>

          <Demo name="Progress" description="" code={`<Progress value={66} />`}>
            <Progress value={progress} className="w-full max-w-xs" aria-label="Demo progress" />
          </Demo>

          <Demo name="Separator" description="" code={`<Separator />`}>
            <div className="w-full max-w-xs space-y-2 text-sm">
              <p>Above</p>
              <Separator />
              <p>Below</p>
            </div>
          </Demo>

          <Demo
            name="Table"
            description=""
            code={`<Table><TableHeader>…</TableHeader><TableBody>…</TableBody></Table>`}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>XP</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell>ash</TableCell>
                  <TableCell>1,204</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>riv</TableCell>
                  <TableCell>980</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Demo>

          <Demo
            name="Skeleton (shadcn)"
            description="Loading placeholder."
            code={`<Skeleton className="h-4 w-40" />`}
          >
            <div className="w-full max-w-xs space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-3/4" />
            </div>
          </Demo>
        </Section>

        {/* Overlays */}
        <Section id="overlays" title="Overlays">
          <Demo
            name="Dialog"
            description="Modal."
            code={`<Dialog>
  <DialogTrigger asChild><Button>Open</Button></DialogTrigger>
  <DialogContent>…</DialogContent>
</Dialog>`}
          >
            <Dialog>
              <DialogTrigger asChild>
                <Button variant="outline">Open dialog</Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Confirm session</DialogTitle>
                  <DialogDescription>You'll start a 25-minute deep focus block.</DialogDescription>
                </DialogHeader>
              </DialogContent>
            </Dialog>
          </Demo>

          <Demo
            name="Popover"
            description=""
            code={`<Popover><PopoverTrigger>…</PopoverTrigger><PopoverContent>…</PopoverContent></Popover>`}
          >
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline">Popover</Button>
              </PopoverTrigger>
              <PopoverContent>Contextual info surfaces here.</PopoverContent>
            </Popover>
          </Demo>

          <Demo
            name="Tooltip"
            description=""
            code={`<TooltipProvider><Tooltip><TooltipTrigger>…</TooltipTrigger><TooltipContent>…</TooltipContent></Tooltip></TooltipProvider>`}
          >
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost">Hover me</Button>
                </TooltipTrigger>
                <TooltipContent>Ambient hint</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </Demo>
        </Section>

        {/* Navigation */}
        <Section id="navigation" title="Navigation">
          <Demo
            name="Tabs"
            description=""
            code={`<Tabs defaultValue="a"><TabsList>…</TabsList><TabsContent>…</TabsContent></Tabs>`}
          >
            <Tabs defaultValue="a" className="w-full max-w-xs">
              <TabsList className="w-full">
                <TabsTrigger value="a" className="flex-1">
                  Focus
                </TabsTrigger>
                <TabsTrigger value="b" className="flex-1">
                  Break
                </TabsTrigger>
              </TabsList>
              <TabsContent value="a" className="pt-3 text-sm text-white/70">
                Deep work.
              </TabsContent>
              <TabsContent value="b" className="pt-3 text-sm text-white/70">
                Reset window.
              </TabsContent>
            </Tabs>
          </Demo>

          <Demo
            name="Accordion"
            description=""
            code={`<Accordion type="single" collapsible>
  <AccordionItem value="a"><AccordionTrigger>Q</AccordionTrigger><AccordionContent>A</AccordionContent></AccordionItem>
</Accordion>`}
          >
            <Accordion type="single" collapsible className="w-full max-w-xs">
              <AccordionItem value="a">
                <AccordionTrigger>What is a session?</AccordionTrigger>
                <AccordionContent>A timed focus block.</AccordionContent>
              </AccordionItem>
              <AccordionItem value="b">
                <AccordionTrigger>Breaches?</AccordionTrigger>
                <AccordionContent>Movement mid-session.</AccordionContent>
              </AccordionItem>
            </Accordion>
          </Demo>
        </Section>

        {/* Feedback */}
        <Section id="feedback" title="Feedback">
          <Demo
            name="Alert"
            description=""
            code={`<Alert><AlertTitle>Heads up</AlertTitle><AlertDescription>…</AlertDescription></Alert>`}
          >
            <Alert className="w-full max-w-sm">
              <AlertTitle>Session queued</AlertTitle>
              <AlertDescription>Will sync when you're back online.</AlertDescription>
            </Alert>
          </Demo>

          <Demo
            name="Sonner toast"
            description="Global toast via sonner."
            code={`import { toast } from "sonner";
toast.success("Session complete");`}
          >
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => toast.success("Session complete")}>
                Success
              </Button>
              <Button variant="outline" onClick={() => toast.error("Sensor lost")}>
                Error
              </Button>
            </div>
          </Demo>
        </Section>

        {/* FX Text */}
        <Section id="fx-text" title="FX — Text">
          <Demo
            name="MatrixText"
            description="Character scramble on mount."
            code={`<MatrixText>PRESENCE</MatrixText>`}
          >
            <MatrixText text="PRESENCE" className="text-2xl font-semibold" />
          </Demo>

          <Demo
            name="ShinyText"
            description="Sweeping specular highlight."
            code={`<ShinyText speed={5}>Enter the room</ShinyText>`}
          >
            <ShinyText speed={5} className="text-2xl font-semibold">
              Enter the room
            </ShinyText>
          </Demo>

          <Demo
            name="NumberTicker"
            description="Animates from 0 to value."
            code={`<NumberTicker value={1204} />`}
          >
            <NumberTicker value={1204} className="text-3xl font-mono text-primary" />
          </Demo>

          <Demo
            name="TextReveal"
            description="Word-by-word fade on scroll."
            code={`<TextReveal text="Non-digital space is a human right." />`}
          >
            <TextReveal className="text-lg text-white/80">
              Non-digital space is a human right.
            </TextReveal>
          </Demo>

          <Demo
            name="ScrollVelocity"
            description="Scroll-driven horizontal marquee."
            code={`<ScrollVelocity>· FOCUS · DEPTH · SILENCE ·</ScrollVelocity>`}
          >
            <div className="w-full overflow-hidden">
              <ScrollVelocity
                words={["FOCUS", "DEPTH", "SILENCE", "PRESENCE"]}
                className="text-2xl font-semibold text-white/70"
              />
            </div>
          </Demo>
        </Section>

        {/* FX Motion */}
        <Section id="fx-motion" title="FX — Motion & Ambient">
          <Demo
            name="Particles"
            description="Interactive drifting particles."
            code={`<Particles quantity={80} />`}
          >
            <div className="relative h-40 w-full overflow-hidden rounded-md">
              <Particles count={80} />
            </div>
          </Demo>

          <Demo name="Meteors" description="Diagonal streaks." code={`<Meteors number={20} />`}>
            <div className="relative h-40 w-full overflow-hidden rounded-md bg-black/60">
              <Meteors count={20} />
            </div>
          </Demo>

          <Demo name="LightRays" description="Ambient god-rays." code={`<LightRays />`}>
            <div className="relative h-40 w-full overflow-hidden rounded-md bg-black">
              <LightRays />
            </div>
          </Demo>

          <Demo name="Ripple" description="Concentric pulse ring." code={`<Ripple />`}>
            <div className="relative flex h-40 w-full items-center justify-center overflow-hidden rounded-md">
              <Ripple />
            </div>
          </Demo>

          <Demo
            name="Marquee"
            description="Infinite scroll strip."
            code={`<Marquee>{items.map(...)}</Marquee>`}
          >
            <div className="w-full overflow-hidden">
              <Marquee className="[--duration:20s]">
                {["Deep work", "Silence", "Presence", "Focus", "Depth"].map((w) => (
                  <span key={w} className="mx-4 text-white/60">
                    {w}
                  </span>
                ))}
              </Marquee>
            </div>
          </Demo>

          <Demo
            name="OrbitingCircles"
            description="Rotating orbit ring."
            code={`<OrbitingCircles radius={60}>{items}</OrbitingCircles>`}
          >
            <div className="relative flex h-52 w-full items-center justify-center">
              <OrbitingCircles
                size={200}
                center={<span className="text-xs text-white/60">core</span>}
                orbits={[
                  {
                    radius: 0.5,
                    duration: 20,
                    items: [<span key="a" className="h-3 w-3 rounded-full bg-primary" />],
                  },
                  {
                    radius: 0.85,
                    duration: 30,
                    reverse: true,
                    items: [<span key="b" className="h-2 w-2 rounded-full bg-white" />],
                  },
                ]}
              />
            </div>
          </Demo>
        </Section>

        {/* FX Skeletons */}
        <Section id="fx-skeleton" title="FX — Skeletons">
          <Demo
            name="MapSkeleton"
            description="Placeholder for the DottedMap while it lazy-loads."
            code={`<MapSkeleton />`}
          >
            <MapSkeleton className="h-40 w-full" />
          </Demo>

          <Demo
            name="MeteorSkeleton"
            description="Placeholder for the Meteors field."
            code={`<MeteorSkeleton />`}
          >
            <div className="h-40 w-full">
              <MeteorSkeleton />
            </div>
          </Demo>
        </Section>
      </main>

      <footer className="border-t border-white/10 py-8 text-center text-xs text-muted-foreground">
        Component catalog · Stack'd design system
      </footer>
    </div>
  );
}
