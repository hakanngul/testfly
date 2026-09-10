---
name: TestFly
description: The Spring Boot of Selenium — High-craft, zero-boilerplate Java test automation framework
colors:
  primary: "#0071e3"
  primary-hover: "#0077ed"
  neutral-bg: "#f5f5f7"
  neutral-surface: "#ffffff"
  neutral-dark-bg: "#000000"
  neutral-dark-surface: "#1c1c1e"
  text-primary: "#1d1d1f"
  text-secondary: "#6e6e73"
  text-muted: "#86868b"
  status-pass: "#97cc64"
  status-fail: "#fd5a3e"
  status-warn: "#ffb238"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', sans-serif"
    fontSize: "clamp(2rem, 4vw, 2.75rem)"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', sans-serif"
    fontSize: "clamp(1.5rem, 3vw, 2rem)"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.03em"
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', sans-serif"
    fontSize: "clamp(1.25rem, 2.2vw, 1.5rem)"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "-0.02em"
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "normal"
  label:
    fontFamily: "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace"
    fontSize: "0.85rem"
    fontWeight: 600
    letterSpacing: "-0.01em"
rounded:
  sm: "10px"
  md: "14px"
  lg: "22px"
  pill: "980px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
    padding: "0 1.4rem"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "rgba(255, 255, 255, 0.72)"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.pill}"
    padding: "0 1.4rem"
    height: "44px"
  card-elevated:
    backgroundColor: "{colors.neutral-surface}"
    rounded: "{rounded.md}"
    padding: "24px"
---

# Design System: TestFly

## Overview

**Creative North Star: "The Cupertino Lab"**

TestFly merges the uncompromising aesthetic polish of Apple hardware product pages with the razor-sharp telemetry clarity of a next-generation engineering testing observatory. Rather than feeling like a clinical, dated developer utility or a noisy SaaS dashboard, the visual system communicates precision, premium craft, quiet speed, and absolute reliability.

Every surface is built around pristine, high-contrast typography, frosted translucent materials (`backdrop-filter: blur(20px)`), and tactile, fluid micro-interactions powered by natural spring physics (`cubic-bezier(0.25, 1, 0.5, 1)`). Backgrounds act as a serene, neutral canvas—light mode evokes an architectural design studio in off-white (`#f5f5f7`), while dark mode delivers deep OLED luxury (`#000000` with `#1c1c1e` elevated tiles). Vibrant Apple Blue (`#0071e3`) is reserved strictly for focal actions and brand accents, preventing visual fatigue.

**Key Characteristics:**
- **Frosted Glass Luminosity:** Real-time blurred navigation bars, floating status filters, and translucent pill controls.
- **Hardware-Grade Tactility:** Pill buttons compress on click (`0.97x`) and lift smoothly on hover (`1.02x` with subtle luminescence).
- **Dual-Surface Consistency:** Shared visual language between the developer documentation portal (`docs-site`) and the interactive test execution timeline (`reporting`).
- **Telemetry Precision:** Clear, un-muddled status indicators (emerald pass, crimson fail, amber warning) using legible monospace code typography.

## Colors

The palette is restrained, premium, and functional—grounded in monochrome neutrals with a singular vibrant accent and high-clarity telemetry status tones.

### Primary
- **Apple Blue** (`#0071e3` light / `#0a84ff` dark): The singular action accent. Used for primary CTA buttons, active sidebar navigation pills, linked references, and key interactive focal points.
- **Electric Blue Hover** (`#0077ed` light / `#409cff` dark): Energetic state shift for hover transitions with a luminous drop shadow.

### Neutral
- **Off-White Canvas** (`#f5f5f7`): Default light mode page background, giving an airy, serene, non-glare foundation.
- **Pure Studio White** (`#ffffff`): Elevated card and content layer in light mode.
- **OLED Midnight** (`#000000`): Pure black backdrop in dark mode, saving battery and maximizing visual contrast.
- **Elevated Graphite** (`#1c1c1e`): Primary container and card background in dark mode.
- **Deep Ink Text** (`#1d1d1f`): High-contrast primary reading text in light mode.
- **Secondary Slate** (`#6e6e73` light / `#b0b0b5` dark): Auxiliary text, timestamps, labels, and inactive navigation links.
- **Muted Platinum** (`#86868b` light / `#8e8e93` dark): Borders, dividers, subtle captions, and inactive icons.

### Telemetry (Status)
- **Telemetry Emerald** (`#97cc64`): Confirmed test pass, healed locator success, healthy assertion check.
- **Telemetry Crimson** (`#fd5a3e`): Test failure, broken assertion, critical error alert.
- **Telemetry Amber** (`#ffb238`): Warning, retry attempt, unstable or flaky locator warning.

### Named Rules
**The Glass Canvas Rule.** Backgrounds never compete with content. Pure white or OLED black acts as the quiet foundation; the primary blue accent is applied to ≤10% of any given viewport so its presence immediately signals actionable intent.

**The Semantic Status Rule.** Status colors (green, red, yellow) belong strictly to test results, validation outcomes, and system alerts. They are never repurposed for generic decorative gradients or arbitrary UI borders.

## Typography

**Display Font:** `-apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", system-ui, sans-serif`
**Body Font:** `-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", system-ui, sans-serif`
**Code & Monospace Font:** `'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace`

**Character:** Typographic hierarchy mirrors the crisp rhythm of macOS system applications: negative tracking on large titles for commanding posture, neutral tracking on body prose for fatigue-free reading, and ligature-rich monospace for code expressions.

### Hierarchy
- **Display** (Bold 700, `clamp(2rem, 4vw, 2.75rem)`, line-height 1.15, letter-spacing `-0.035em`): Hero headlines and major marketing declarations.
- **Headline** (Bold 700, `clamp(1.5rem, 3vw, 2rem)`, line-height 1.25, letter-spacing `-0.03em`): Feature section headers and document titles.
- **Title** (Bold 700, `clamp(1.25rem, 2.2vw, 1.5rem)`, line-height 1.3, letter-spacing `-0.02em`): Bento card titles, modal headers, subsection dividers.
- **Body** (Regular 400 / Semibold 600, `1rem / 16px`, line-height 1.55, letter-spacing `normal`): Documentation text, explanatory guides, test steps. Max line length 65–75ch for prose.
- **Label** (Semibold 600, `0.8125rem / 13px`, line-height 1.2, letter-spacing `-0.01em`): Navigation links, table headers, button text.
- **Code** (Medium 500, `0.85em`, letter-spacing `normal`): Inline code spans, assertion locators, log outputs.

### Named Rules
**The Tightening Tracking Rule.** As font size increases, tracking tightens proportionally (from `-0.01em` on labels to `-0.035em` on display). Large titles feel sculpted, not loose.

**The Code Integrity Rule.** Every code block, locator expression, stack trace, and JSON metric must render in monospace with a dedicated syntax-highlighted background (`#f0f4f8` light / `#1c1c1e` dark).

## Layout

The spatial model uses an intentional, comfortable container grid with generous breathing room and clear visual hierarchy.

- **Max Container Width:** 1280px for documentation and landing pages; fluid width with 240px fixed collapsible sidebar for reporting dashboards.
- **Content Max Width:** 840px for article prose, ensuring optimal reading speed without head-turning strain.
- **Grid Systems:** 12-column adaptive grid on desktop, collapsing to 2-column or 1-column bento grids on mobile viewports (< 768px).
- **Spacing Rhythm:** Standard 8px spatial grid: `8px`, `16px`, `24px`, `32px`, `48px`, `64px`.

### Responsive Breakpoints
- **sm (640px):** Single-column layout; navigation collapses to fullscreen or slide-over drawer; pill buttons stretch to full width where appropriate.
- **md (768px):** Bento cards convert from single-column to balanced 2-column masonry grids.
- **lg (996px):** Sidebar navigation becomes pinned; table of contents (TOC) appears on right rail.
- **xl (1280px):** Full desktop layout with max container margins.

## Elevation & Depth

TestFly uses a **frosted translucent layering** model rather than heavy opaque drop shadows. Depth is communicated through optical blur, luminous hairline borders, and subtle ambient shadows.

### Shadow Vocabulary
- **Subtle Rim** (`0 1px 2px rgba(0, 0, 0, 0.04)`): Micro edge separation for pills, chips, and table rows.
- **Card Rest** (`0 4px 16px rgba(0, 0, 0, 0.06)`): Resting state for bento tiles and documentation cards.
- **Floating Glass** (`0 12px 40px rgba(0, 0, 0, 0.10)`): Floating navigation bars, active dropdowns, and flyout modals.
- **Button Glow** (`0 4px 12px rgba(0, 113, 227, 0.25)`): Gentle luminescent accent halo underneath primary CTA buttons.

### Named Rules
**The Translucent Surface Rule.** Overlaid UI elements (navigation bars, floating step logs) must utilize `backdrop-filter: blur(20px)` with a 72%–85% semi-transparent surface background and a 1px hairline border (`rgba(255, 255, 255, 0.5)` light / `rgba(255, 255, 255, 0.12)` dark).

**The Flat-at-Rest Rule.** Cards and containers sit quietly on their canvas with minimal resting shadows. Elevation is reserved for active states, hovering, and elevated dialogs.

## Shapes

The form language is rounded, friendly, and organic—drawing direct inspiration from Apple's squircle geometry and pill-shaped touch targets.

- **Small Radius (10px):** Interactive chips, search bars, code blocks, and table cells.
- **Medium Radius (14px):** Bento tiles, documentation cards, alert callouts, and step execution containers.
- **Large Radius (22px):** Hero visual preview frames, modal dialog wrappers, and feature banners.
- **Pill (980px / 999px):** All buttons, category tags, version pills, and navigation links.

### Named Rules
**The Pure Pill Action Rule.** Every standalone button and clickable filter tag must have a complete pill border-radius (`980px`). Boxy, sharp-cornered buttons are strictly prohibited.

## Components

### Buttons
- **Shape:** Full pill radius (`980px`), minimum touch target height `44px`.
- **Primary:** Apple Blue background (`#0071e3`), white text, bold font (`650` weight), subtle glow shadow (`0 4px 12px rgba(0, 113, 227, 0.22)`).
- **Secondary:** Translucent frosted material background (`rgba(255, 255, 255, 0.72)` light / `rgba(28, 28, 30, 0.8)` dark), 1px border (`var(--tf-material-border)`), primary dark text.
- **Hover / Active State:** Smooth spring scaling: `transform: scale(1.02) translateY(-1px)` on hover; `transform: scale(0.97)` on active click with `80ms` instant recovery.

### Cards / Bento Tiles
- **Corner Style:** Smooth squircle radius (`14px` or `22px`).
- **Background:** `rgba(255, 255, 255, 0.85)` in light mode; `rgba(44, 44, 46, 0.65)` in dark mode.
- **Border:** Hairline border (`1px solid var(--tf-card-border)`).
- **Internal Padding:** `24px` on desktop, `16px` on mobile.
- **Hover Behavior:** Gentle border luminescence shift and subtle lift (`translateY(-2px)`).

### Navigation Bar
- **Style:** Frosted glass sticky banner, `52px` height, `backdrop-filter: blur(20px) saturate(180%)`.
- **Brand Logo:** `26px` square icon with `7px` rounded corners.
- **Links:** Pill hover background (`rgba(0, 0, 0, 0.04)` light / `rgba(255, 255, 255, 0.08)` dark), active bold state.

### Code Blocks
- **Style:** Dedicated dark/light container (`#f0f4f8` light / `#1c1c1e` dark), rounded `14px`, 1px border.
- **Copy Button:** Discrete top-right floating pill with icon that confirms with checkmark animation upon copy.
- **Highlighted Line:** Soft accent tint (`rgba(0, 113, 227, 0.08)` light / `rgba(10, 132, 255, 0.15)` dark).

### Telemetry Badges & Chips
- **Shape:** Pill radius (`980px`), padding `0.25rem 0.65rem`, font size `0.75rem`, font-weight `700`.
- **Pass Badge:** Emerald background tint with bold green text (`Pass: 100%`).
- **Fail Badge:** Crimson background tint with bold red text (`Fail: 2`).
- **Flaky Badge:** Amber background tint with warning icon (`Flaky: 1`).

## Do's and Don'ts

### Do:
- **Do** maintain the 44px minimum touch target height for all interactive buttons and navigation links.
- **Do** use `backdrop-filter: blur(...)` combined with translucent surface backgrounds for sticky headers and elevated overlays.
- **Do** preserve the spring timing `cubic-bezier(0.25, 1, 0.5, 1)` across hover transforms and micro-interactions.
- **Do** test all color pairings against WCAG AA standards (minimum 4.5:1 contrast for normal text).
- **Do** respect the `prefers-reduced-motion` media query by falling back to instant `0.01ms` transitions.

### Don't:
- **Don't** use generic saturated blues (like `#0000ff`) or raw unstyled borders; stick to the curated Apple Blue token (`#0071e3`).
- **Don't** create sharp 90-degree rectangle buttons; always use the `980px` pill radius for action triggers.
- **Don't** apply heavy, muddy black drop shadows (`rgba(0, 0, 0, 0.5)`) in light mode; use subtle, multi-stage ambient shadows.
- **Don't** use status colors (emerald, crimson, amber) for decorative backgrounds or marketing slogans.
- **Don't** nest interactive controls inside other interactive clickable surfaces.
