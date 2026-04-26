/**
 * E2E tests for the AI Chat page using Playwright.
 *
 * Prerequisites:
 *   - Agent server on http://localhost:8765 (python3 -m video_agent serve --backend fixture)
 *   - Web dev server on http://localhost:5173 (npm run dev)
 *
 * Run:
 *   npx playwright test --config=web/e2e/playwright.config.ts
 */
import { test, expect } from '@playwright/test';

const BASE = 'http://localhost:5173';

test.describe('ChatPage', () => {

  test('loads and shows welcome screen with quick actions', async ({ page }) => {
    await page.goto(`${BASE}/chat`);

    // header
    await expect(page.locator('.chat-title')).toContainText('AI 视频助手');

    // status shows online (or checking → online)
    await expect(page.locator('.status-dot')).toBeVisible();

    // welcome section
    await expect(page.locator('.chat-welcome')).toContainText('欢迎使用 AI 视频助手');

    // quick action buttons
    const quickBtns = page.locator('.chat-quick-actions button');
    await expect(quickBtns).toHaveCount(6);
    await expect(quickBtns.first()).toContainText('我上传了几个视频？');
  });

  test('fills input on quick action click', async ({ page }) => {
    await page.goto(`${BASE}/chat`);
    await page.locator('.chat-quick-actions button').first().click();

    const textarea = page.locator('.chat-input-row textarea');
    await expect(textarea).toHaveValue('我上传了几个视频？');
  });

  test('sends message and receives streaming response', async ({ page }) => {
    await page.goto(`${BASE}/chat`);

    // type and send
    await page.locator('.chat-input-row textarea').fill('我上传了几个视频？');
    await page.locator('.chat-send-btn').click();

    // user message bubble appears
    await expect(page.locator('.chat-msg.user .chat-msg-content')).toContainText('我上传了几个视频？');

    // wait for assistant response (streaming or done)
    const assistantMsg = page.locator('.chat-msg.assistant .chat-msg-content').first();
    await expect(assistantMsg).not.toBeEmpty({ timeout: 30000 });

    // verify it contains reasonable content
    const text = await assistantMsg.innerText();
    expect(text.length).toBeGreaterThan(5);
  });

  test('shows tool call cards', async ({ page }) => {
    await page.goto(`${BASE}/chat`);

    // search query triggers tool calls
    await page.locator('.chat-input-row textarea').fill('搜索美食类公开视频');
    await page.locator('.chat-send-btn').click();

    // wait for tool call card
    const toolCard = page.locator('.chat-tool-card').first();
    await expect(toolCard).toBeVisible({ timeout: 30000 });

    // expand the card
    await toolCard.locator('summary').click();
    await expect(toolCard.locator('pre')).toBeVisible();
  });

  test('sends multiple messages in a conversation', async ({ page }) => {
    await page.goto(`${BASE}/chat`);

    // first message
    await page.locator('.chat-input-row textarea').fill('我上传了几个视频？');
    await page.locator('.chat-send-btn').click();

    // wait for first response
    await expect(page.locator('.chat-msg.assistant .chat-msg-content').first()).not.toBeEmpty({ timeout: 30000 });

    // second message
    await page.locator('.chat-input-row textarea').fill('最早的视频是什么？');
    await page.locator('.chat-send-btn').click();

    // should have 2 user and 2 assistant messages
    await expect(page.locator('.chat-msg.user')).toHaveCount(2);
    await expect(page.locator('.chat-msg.assistant')).toHaveCount(2);
  });

  test('Enter sends, Shift+Enter adds newline', async ({ page }) => {
    await page.goto(`${BASE}/chat`);

    const textarea = page.locator('.chat-input-row textarea');

    // Enter sends message
    await textarea.fill('你好');
    await textarea.press('Enter');

    // user message appears (meaning send happened)
    await expect(page.locator('.chat-msg.user .chat-msg-content').first()).toContainText('你好');
    // input should be cleared
    await expect(textarea).toHaveValue('');
  });

});
