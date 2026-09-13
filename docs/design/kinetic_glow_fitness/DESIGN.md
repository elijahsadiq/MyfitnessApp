---
name: Kinetic Glow Fitness
colors:
  surface: '#f9faf6'
  surface-dim: '#d9dad7'
  surface-bright: '#f9faf6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f0'
  surface-container: '#edeeea'
  surface-container-high: '#e7e9e5'
  surface-container-highest: '#e2e3df'
  on-surface: '#1a1c1a'
  on-surface-variant: '#454837'
  inverse-surface: '#2e312f'
  inverse-on-surface: '#f0f1ed'
  outline: '#767965'
  outline-variant: '#c6c8b2'
  surface-tint: '#536600'
  primary: '#536600'
  on-primary: '#ffffff'
  primary-container: '#d4f26a'
  on-primary-container: '#5a6e00'
  inverse-primary: '#b6d24e'
  secondary: '#5d5f5b'
  on-secondary: '#ffffff'
  secondary-container: '#e3e3dd'
  on-secondary-container: '#636561'
  tertiary: '#5c5f59'
  on-tertiary: '#ffffff'
  tertiary-container: '#e4e6de'
  on-tertiary-container: '#646760'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d1ef67'
  primary-fixed-dim: '#b6d24e'
  on-primary-fixed: '#171e00'
  on-primary-fixed-variant: '#3e4c00'
  secondary-fixed: '#e3e3dd'
  secondary-fixed-dim: '#c6c7c2'
  on-secondary-fixed: '#1a1c19'
  on-secondary-fixed-variant: '#464743'
  tertiary-fixed: '#e2e3db'
  tertiary-fixed-dim: '#c5c7bf'
  on-tertiary-fixed: '#191c18'
  on-tertiary-fixed-variant: '#454842'
  background: '#f9faf6'
  on-background: '#1a1c1a'
  surface-variant: '#e2e3df'
typography:
  display-metric:
    fontFamily: Plus Jakarta Sans
    fontSize: 44px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes an editorial, high-performance personal fitness experience tailored natively for Android platforms following Material Design 3 guidelines. The aesthetic is clean, athletic, and focused—merging modern functional minimalism with tactile, high-contrast digital utility. 

Rather than overloading the user with synthetic gamification or sensory noise, the visual language relies on pure clarity: a calming warm off-white canvas juxtaposed with deep charcoal technical surfaces and a hyper-visible, energetic electric lime accent. It evokes momentum, precision, discipline, and daily accomplishment. 

The experience targets dedicated athletes, runners, weightlifters, and everyday health optimizers who value uncluttered telemetry, clear cadence, and quick glances during mid-workout sessions. Interactions prioritize thumb-friendly ergonomics, ultra-legible typography scales, and immediate state feedback.

## Colors

The palette is engineered around high legibility, strict contrast accessibility, and deliberate chromatic hierarchy:

- **Surface & Backgrounds**:
  - Main App Canvas: `#F5F6F2` (Warm Off-White) provides an organic, non-fatiguing backdrop.
  - Standard Cards, Inputs & Elevated Containers: `#FFFFFF` (Pure White).
  - Dark Featured & Telemetry Surfaces: `#121411` (Near-Black) and `#1C1F1A` (Charcoal) for high-impact metric summaries, night runs, and workout dashboards.
- **Typography & Structural Lines**:
  - Primary Text: `#111410` (95% contrast against light backgrounds).
  - Secondary / Muted Text: `#6B7066` (Used for metadata, units, and timestamps).
  - Dividers & Structural Borders: `#E4E6E0` (Subtle 1px perimeter definition).
- **Single Accent — Electric Lime (`#D4F26A`)**:
  - Used strictly for high-priority interactive touchpoints: Primary CTA buttons, Floating Action Buttons (FAB), active navigation pills, selected filter chips, progress rings/meters, and active GPS route lines.
  - **Strict Legibility Rule**: Any typography or iconography rendered on top of `#D4F26A` must be `#111410`. Never use `#D4F26A` as text on light surfaces.
- **Functional Validation**:
  - Error Red: `#D64545`, dedicated solely to destructive states, missed targets, and form validation alerts.

## Typography

Typography uses **Plus Jakarta Sans** across all levels, harnessing its geometric structure, wide apertures, and clean vertical metrics for rapid glanceability:

- **Metrics & Telemetry (`display-metric`)**: 44px ExtraBold, tight line height and negative letter tracking. Always paired horizontally or as a stacked sub-label with `label-sm` or `label-md` in `#6B7066` for units (`BPM`, `KM`, `KCAL`, `MIN`).
- **Screen Titles (`headline-lg`)**: 28px Bold (`28sp`), acting as the primary anchor for top-app bars and landing screens.
- **Section Headers (`headline-sm`)**: 18px SemiBold (`18sp`), structuring daily logs, split metrics, and workout routines.
- **Body (`body-lg` / `body-md`)**: 15px Regular (`15sp`), ensuring clear readability for workout notes, health insights, and configuration panels.
- **Captions & Microcopy (`label-md` / `label-sm`)**: 13px Medium (`13sp`), rendered strictly in `#6B7066` for contextual secondary indicators, timestamps, and resting states.

## Layout & Spacing

Designed specifically for modern Android viewport widths (typically 390dp to 412dp portrait):

- **Grid & Margins**: An 8dp structural baseline rhythm is adhered to globally. The outer viewport margin is fixed at `20px` (`1.25rem`), keeping content clear of display curvature while maximizing usable surface area.
- **Vertical Hierarchy**: Major content blocks and card stacks maintain `24px` (`space-lg`) separation. Micro-elements, such as metric rows inside cards or badge rows, use `8px` (`space-sm`) or `12px` gaps.
- **System Integration**:
  - Top edge observes Android system status bar safe-area insets (clock, network status, battery icons).
  - Bottom edge supports edge-to-edge rendering with dedicated clearance for the Android 3-button or pill gesture navigation bar (minimum 48dp bottom clearance).
  - Interactive touch bounds strictly enforce a minimum `48dp x 48dp` target size.

## Elevation & Depth

To avoid visual weight and preserve battery efficiency, the design system repudiates heavy drop shadows in favor of **tonal layering and crisp 1px borders**:

- **Layer 0 (Canvas)**: `#F5F6F2` canvas baseline.
- **Layer 1 (Cards & Standard Modules)**: `#FFFFFF` resting directly on the canvas, bounded by a continuous 1px `#E4E6E0` border. No drop shadow is applied in the resting state.
- **Layer 2 (Featured Telemetry / Hero Cards)**: Rendered in inverted tonal contrast (`#121411` or `#1C1F1A`). Borders on dark surfaces use a 1px tint of `rgba(255, 255, 255, 0.08)`.
- **Floating Controls & FABs**: Elevated using an ultra-subtle ambient lift: `0px 6px 16px rgba(18, 20, 17, 0.08)`. Hover or pressed states drop ambient blur and scale down subtly (0.98x) for tactile haptic feedback.

## Shapes

Shape identity is defined by organic, ergonomic radii tailored to hand-held device interaction:

- **Cards & Data Modules**: Exactly `24px` corner radius (`rounded-3xl` / custom continuous curves), imparting a modern pebble-like smoothness.
- **Buttons, Pills & Interactive Chips**: Fully rounded pill shapes (`rounded-full` / 9999px radius) for all action buttons, selection chips, and floating action controls.
- **Input Fields & Search Bars**: `16px` corner radius, creating clear visual distinction from primary buttons and cards.
- **Icon Tiles**: `44px x 44px` squircle containers with a `14px` radius, housing activity categories (running, cycling, strength training).

## Components

### Buttons & Floating Action Controls
- **Primary CTA**: Full pill shape (`height: 52px`), solid `#D4F26A` fill, with `#111410` SemiBold label and optional leading 24px icon. Minimum touch target `48dp`.
- **Secondary / Ghost Button**: Full pill shape, `#FFFFFF` background with 1px `#E4E6E0` border, `#111410` text. Pressed state transitions to `#F5F6F2`.
- **Floating Action Button (FAB)**: Full pill or 56px circle, `#D4F26A` background, `#111410` icon/text, positioned 16dp above the navigation bar.

### Chips & Filter Tabs
- **Default Chip**: Height 36px, full pill, `#FFFFFF` surface with 1px `#E4E6E0` stroke, 13px medium `#6B7066` text.
- **Selected Chip**: Solid `#D4F26A` background, 1px `#D4F26A` stroke, `#111410` SemiBold text.

### Cards & Telemetry Containers
- **Standard Card**: White `#FFFFFF`, 24px corner radius, 1px `#E4E6E0` border, 16px internal padding.
- **Dark Telemetry Hero**: Background `#121411` or `#1C1F1A`, 24px radius. Contains primary metrics in `#FFFFFF`, units and secondary labels in `#6B7066`, and active telemetry / sparklines / progress arcs rendered in `#D4F26A`.

### Lists & Data Rows
- Height 56dp–72dp per row. Separated by 1px `#E4E6E0` inset dividers.
- Left-aligned 44px squircle icon container (`#F5F6F2` fill, 14px radius) paired with two-line textual grouping (Primary label in 15px `#111410`, subtext in 13px `#6B7066`).

### Input Fields
- Height 52px, 16px radius, pure white `#FFFFFF` surface, 1px `#E4E6E0` border.
- Active/Focused: 1.5px border in `#111410`.
- Error State: 1.5px border in `#D64545` with trailing alert icon and caption in `#D64545`.

### Iconography & System Nav
- **Material Symbols Rounded**: Outline style, consistent 24px bounding box and 2px stroke weight. Specialized sport glyphs: `directions_run`, `directions_bike`, and `fitness_center`.
- **Bottom Navigation Bar**: Fixed bottom bar with light `#FFFFFF` surface, top 1px border `#E4E6E0`. Active state utilizes a pill container behind the icon flooded with `#D4F26A` and icon in `#111410`. Inactive icons remain `#6B7066`.