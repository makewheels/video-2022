## 签名设计
加签种类：
1. 用户请求后端
2. 后端应用内部调用
3. 开放第三方接口

### 1. 用户请求后端

### 2. 后端应用内部调用，比如云函数回调
#### 业务场景
后端应用内部调用，比如云函数回调
#### 签名方法
urlEncode(base64(HmacSHA256(

url中的参数，去掉sign字段，按字母排序 + "\n"

body + "\n" +

key

)))
#### 代码调用
```java
public static String hmac(String key, String data) {
    HMac hMac = SecureUtil.hmacSha256(key);
    return hMac.digestBase64(data, true);
}
```

### 3. 开放第三方接口
