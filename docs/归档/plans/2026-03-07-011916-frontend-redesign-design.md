> **状态:** ✅ 已完成 — [PR #14](https://github.com/makewheels/video-2022/pull/14)

# Frontend Redesign — YouTube-like UI

## Problem
All 6 frontend pages are bare-bones HTML with no consistent styling, no responsive design, and poor mobile experience.

## Approach
Vanilla HTML/CSS/JS redesign with a shared `global.css` (CSS variables for dark/light theming), mobile-first responsive layout, YouTube-inspired visual design.

## Design Decisions
- **No framework** — vanilla HTML/CSS/JS, no build step
- **CSS variables** for theming (dark/light via `prefers-color-scheme` + manual toggle in `localStorage`)
- **Mobile-first** breakpoints: 375px (mobile), 768px (tablet), 1200px (desktop)
- **Shared files**: `global.css`, `global.js` (theme toggle, auth check, URL utils)
- **TDD**: Playwright tests written first, then implementation
- **Color palette** (light): bg #fff, text #0f0f0f, secondary #606060, accent #065fd4
- **Color palette** (dark): bg #0f0f0f, text #f1f1f1, secondary #aaa, accent #3ea6ff

## Pages (6 total)

### 1. watch.html (Thymeleaf template)
- Full-width 16:9 player container
- Video info: title, view count, date, description
- Playlist sidebar on desktop → stacked below on mobile
- "Copy current time" button styled

### 2. login.html
- Centered card (max 400px)
- Styled phone input + verification code
- Loading state on buttons
- Error message styling

### 3. upload.html
- Card-based form
- Drag-and-drop file zone (fallback: file input)
- Animated progress bar
- Playlist selector dropdown
- Watch URL display with copy button

### 4. statistics.html
- Dashboard card
- Responsive chart container
- Styled date pickers and query buttons

### 5. transfer-youtube.html
- Simple centered card with URL input + submit

### 6. save-token.html
- Loading spinner with redirect message

## Testing Strategy (Playwright TDD)
- Install playwright as dev dependency
- Tests in `video/src/test/playwright/`
- Test categories:
  - Responsive layout (mobile/tablet/desktop viewports)
  - Dark/light mode toggle + system preference
  - Page elements render correctly
  - Navigation flows (login → save-token → redirect)
  - Upload form elements
  - Watch page video container sizing

## Todos
1. Set up Playwright + write tests
2. Create global.css with CSS variables and responsive grid
3. Create global.js with theme toggle and shared utilities
4. Redesign watch.html
5. Redesign login.html
6. Redesign upload.html
7. Redesign statistics.html + transfer-youtube.html + save-token.html
8. Run Playwright tests to verify
9. Commit, push, create PR
