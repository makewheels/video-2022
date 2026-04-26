import { chromium } from 'playwright';

(async () => {
  console.log('🚀 启动浏览器测试...');
  const browser = await chromium.launch({ headless: false, slowMo: 300 });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  try {
    // 1. 访问聊天页面
    console.log('📄 打开页面 http://localhost:5173/chat');
    await page.goto('http://localhost:5173/chat');
    await page.waitForLoadState('networkidle');

    // 2. 设置 token
    console.log('🔑 设置 token...');
    await page.evaluate(() => {
      localStorage.setItem('token', '2048349491580502016');
    });
    await page.reload();
    await page.waitForLoadState('networkidle');
    console.log('✅ Token 已设置并刷新页面');

    // 3. 输入问题
    console.log('⌨️  输入问题...');
    const textarea = page.locator('textarea');
    await textarea.fill('我上传了几个视频？');
    console.log('✅ 问题已输入');

    // 4. 点击发送按钮
    console.log('🖱️  点击发送按钮...');
    const sendButton = page.locator('button.chat-send-btn').first();
    await sendButton.click();
    console.log('✅ 已点击发送');

    // 5. 等待工具调用卡片出现
    console.log('⏳ 等待工具调用卡片...');
    await page.waitForSelector('.chat-tool-card', { timeout: 30000 });
    console.log('✅ 工具调用卡片已出现');

    // 6. 等待流式返回完成
    await page.waitForTimeout(3000);

    // 7. 检查卡片是否展开
    const toolCard = page.locator('.chat-tool-card').first();
    const isOpen = await toolCard.evaluate(el => el.hasAttribute('open'));
    console.log(`📋 工具卡片展开状态: ${isOpen ? '✅ 已展开' : '❌ 未展开'}`);

    // 8. 检查 JSON 内容
    const jsonElements = page.locator('.chat-tool-json');
    const count = await jsonElements.count();
    console.log(`📄 找到 ${count} 个 JSON 元素`);

    if (count > 0) {
      const firstJson = await jsonElements.first().textContent();
      console.log(`📄 第一个 JSON 长度: ${firstJson.length} 字符`);
      console.log(`📄 JSON 前 200 字符:\n${firstJson.substring(0, 200)}`);

      // 检查格式化
      const hasNewlines = firstJson.includes('\n');
      const hasIndentation = firstJson.includes('  ');
      console.log(`✨ JSON 格式化检查:`);
      console.log(`   - 包含换行: ${hasNewlines ? '✅' : '❌'}`);
      console.log(`   - 包含缩进: ${hasIndentation ? '✅' : '❌'}`);
      console.log(`   - 格式化状态: ${hasNewlines && hasIndentation ? '✅ 已格式化' : '❌ 未格式化'}`);
    }

    // 9. 截图
    console.log('📸 截图中...');
    await page.screenshot({ path: '/tmp/chat-ui-test.png', fullPage: true });
    console.log('✅ 截图已保存到 /tmp/chat-ui-test.png');

    await page.waitForTimeout(2000);
    console.log('\n🎉 测试完成！');

  } catch (error) {
    console.error('❌ 测试失败:', error.message);
    await page.screenshot({ path: '/tmp/chat-ui-error.png', fullPage: true });
    console.log('错误截图已保存到 /tmp/chat-ui-error.png');
  } finally {
    await browser.close();
  }
})();
