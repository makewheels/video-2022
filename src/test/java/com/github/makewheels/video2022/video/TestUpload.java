package com.github.makewheels.video2022.video;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.github.makewheels.video2022.utils.FilepathUtils;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Date;

/**
 * <a href="https://chromedriver.storage.googleapis.com/index.html?path=112.0.5615.49/">chromedriver下载</a>
 */
public class TestUpload {
    private WebDriver webDriver;

    public WebDriver startChrome() {
        System.setProperty("webdriver.chrome.driver", FilepathUtils.getChromeDriver().getAbsolutePath());
        ChromeOptions options = new ChromeOptions();
        // https://stackoverflow.com/questions/75678572
        options.addArguments("--remote-allow-origins=*");

        WebDriver webDriver = new ChromeDriver(options);
//        webDriver.manage().window().maximize();
        this.webDriver = webDriver;
        return webDriver;
    }

    public void register() {
        String phone = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_PATTERN);
        // 填手机号
        webDriver.findElement(By.id("input_phone")).sendKeys("SELENIUM_USER-" + phone);
        // 点击请求验证码按钮
        webDriver.findElement(By.id("btn_requestVerificationCode")).click();
        // 填验证码
        webDriver.findElement(By.id("input_verificationCode")).sendKeys("111");
        // 点击提交验证码按钮
        webDriver.findElement(By.id("btn_submitVerificationCode")).click();
    }

    @Test
    public void run() {
        startChrome();
        webDriver.get("http://localhost:5022/upload.html");
        register();
    }
}
