# Contributing

First off, thank you for considering contributing to **Stack'D**.

Whether you're fixing a bug, improving documentation, refining animations, adding features, or reviewing code, every contribution helps make the platform better.

---

# Philosophy

Stack'D is built around three principles:

- **Performance first** — Every interaction should feel instant.
- **Honest engineering** — Never fake progress, statistics, or state.
- **Quality over quantity** — We'd rather merge one excellent PR than ten rushed ones.

When contributing, prioritize readability, maintainability, and correctness over cleverness.

---

# Before You Start

Please:

- Search existing issues before creating a new one.
- Open an issue for major features before spending significant time implementing them.
- Keep pull requests focused on a single problem whenever possible.

Large architectural changes should be discussed before implementation.

---

# Development Setup

## Prerequisites

- Bun (recommended) or Node.js
- Git
- A Supabase project
- Modern Chromium browser

Clone the repository:

```bash
git clone https://github.com/sasly2048/Stack-D.git

cd Stack-D

bun install
```

Copy the environment variables:

```bash
cp .env.example .env
```

Add your:

- Supabase URL
- Supabase Publishable Key

Run locally:

```bash
bun run dev
```

---

# Project Structure

```
src/
├── components/
├── hooks/
├── integrations/
├── lib/
├── routes/
└── ...

supabase/
└── migrations/

tests/
└── visual/
```

Please keep new files within their respective domain.

Avoid dumping unrelated utilities into shared folders.

---

# Coding Standards

## TypeScript

- Prefer explicit types.
- Avoid `any`.
- Use descriptive variable names.
- Keep functions small.

Example:

```ts
function calculateXp(score: number): number {
  ...
}
```

instead of

```ts
function calc(x:any){
...
}
```

---

## React

- Prefer functional components.
- Keep components focused.
- Extract reusable logic into hooks.
- Avoid unnecessary state.

---

## Styling

- Tailwind CSS v4
- Follow existing spacing scale
- Reuse design tokens
- Avoid inline styles unless absolutely necessary

---

## Animations

Motion is part of Stack'D's identity.

Animations should be:

- smooth
- purposeful
- interruptible
- performant

Avoid excessive animation that delays interaction.

---

# Backend Guidelines

All reward-critical logic belongs on the server.

Never trust client-calculated:

- XP
- achievements
- streaks
- progression
- rewards

Those values must always be recomputed server-side.

---

# Database

Every new table should include:

- Row-Level Security
- appropriate policies
- indexes when required

Avoid bypassing RLS.

Never expose service-role credentials.

---

# AI Guidelines

Atlas should never invent information.

All recommendations must be grounded in actual user session history.

If data doesn't exist,
Atlas should explicitly say it doesn't know.

Never fabricate:

- productivity trends
- focus hours
- streaks
- statistics
- achievements

---

# Security

Security-related pull requests are especially welcome.

Areas include:

- authentication
- authorization
- RLS policies
- abuse prevention
- rate limiting
- moderation
- AI safety

Please avoid publicly disclosing vulnerabilities before they've been addressed.

---

# Testing

Before opening a pull request, run:

```bash
bun run lint

bun run format

bun run build
```

If your change affects visuals or animations, also run:

```bash
bun playwright test
```

Please verify:

- desktop layout
- mobile layout
- dark mode
- reduced motion
- low power mode

---

# Pull Requests

A good pull request should:

- solve one problem
- explain why the change exists
- include screenshots for UI changes
- remain reasonably sized
- pass all checks

Please avoid mixing formatting-only changes with functional changes.

---

# Commit Messages

Prefer conventional commits.

Examples:

```
feat: add mentor invitations

fix: prevent duplicate XP rewards

refactor: simplify room synchronization

docs: update README

style: improve dashboard spacing
```

---

# Feature Requests

When proposing a feature, include:

- the problem
- proposed solution
- alternatives considered
- expected user impact

The more context provided, the easier the discussion.

---

# Reporting Bugs

Please include:

- operating system
- browser
- reproduction steps
- expected behavior
- actual behavior
- screenshots (if applicable)

---

# Code of Conduct

Be respectful.

Constructive feedback is encouraged.

Personal attacks, harassment, discrimination, or abusive behavior will not be tolerated.

Our goal is to build a welcoming community for everyone.

---

# Questions

If you're unsure about anything, feel free to open a discussion before starting implementation.

We're happy to help point you in the right direction.

---

# Thank You ❤️

Every issue opened, typo fixed, pull request submitted, and idea shared helps make Stack'D better.

Thank you for contributing.