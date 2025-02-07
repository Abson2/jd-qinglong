package com.meread.selenium.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.meread.selenium.bean.Point;
import com.meread.selenium.bean.*;
import com.meread.selenium.config.HttpClientUtil;
import com.meread.selenium.util.*;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.openqa.selenium.*;
import org.openqa.selenium.html5.LocalStorage;
import org.openqa.selenium.remote.RemoteExecuteMethod;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.html5.RemoteWebStorage;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.meread.selenium.util.CommonAttributes.mockCaptcha;

/**
 * Created by yangxg on 2021/9/1
 *
 * @author yangxg
 */
@Service
@Slf4j
public class JDService implements CommandLineRunner {

    public static final Set<String> NODEJS_PUSH_KEYS = new HashSet<>();
    public static Map<Integer, List<List<Point>>> MOCK_CAPTCHA_POINTS_MAP = new HashMap<>();
    static Pattern pattern = Pattern.compile("data:image.*base64,(.*)");

    static {
        NODEJS_PUSH_KEYS.add("PUSH_KEY");
        NODEJS_PUSH_KEYS.add("BARK_PUSH");
        NODEJS_PUSH_KEYS.add("BARK_SOUND");
        NODEJS_PUSH_KEYS.add("BARK_GROUP");
        NODEJS_PUSH_KEYS.add("TG_BOT_TOKEN");
        NODEJS_PUSH_KEYS.add("TG_USER_ID");
        NODEJS_PUSH_KEYS.add("TG_PROXY_HOST");
        NODEJS_PUSH_KEYS.add("TG_PROXY_PORT");
        NODEJS_PUSH_KEYS.add("TG_PROXY_AUTH");
        NODEJS_PUSH_KEYS.add("TG_API_HOST");
        NODEJS_PUSH_KEYS.add("DD_BOT_TOKEN");
        NODEJS_PUSH_KEYS.add("DD_BOT_SECRET");
        NODEJS_PUSH_KEYS.add("QYWX_KEY");
        NODEJS_PUSH_KEYS.add("QYWX_AM");
        NODEJS_PUSH_KEYS.add("IGOT_PUSH_KEY");
        NODEJS_PUSH_KEYS.add("PUSH_PLUS_TOKEN");
        NODEJS_PUSH_KEYS.add("PUSH_PLUS_USER");
        NODEJS_PUSH_KEYS.add("GOBOT_URL");
        NODEJS_PUSH_KEYS.add("GOBOT_TOKEN");
        NODEJS_PUSH_KEYS.add("GOBOT_QQ");
    }

    public boolean initSuccess;
    @Autowired
    private BaseWebDriverManager driverFactory;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private HttpClientUtil httpClientUtil;

    //关闭hub：http://{hubhost}:{hubport}/lifecycle-manager/LifecycleServlet?action=shutdown
    //关闭node：http://localhost:5557/extra/LifecycleServlet?action=shutdown
    //获取所有会话：http://localhost:4444/grid/api/sessions
    //获取hub状态：http://localhost:4444/grid/api/hub/
    //关闭session：http://localhost:5556/wd/hub/session/e0843c23ae1c81e17c87217f973fc503
    //cURL --request DELETE http://localhost:5556/wd/hub/session/e0843c23ae1c81e17c87217f973fc503

//    新的api
    //http://localhost:4444/status 获取hub的状态
    //http://localhost:4444/ui/index.html#/ 后台ui界面

    //    cURL --request DELETE 'http://172.18.0.8:5555/se/grid/node/session/a73dade333fd8b68224ca762f087d676' --header 'X-REGISTRATION-SECRET;'
//    cURL --request GET 'http://<node-URL>/se/grid/node/owner/<session-id>' --header 'X-REGISTRATION-SECRET;'
    @Autowired
    private FreeMarkerConfigurer freeMarkerConfigurer;

    public static String strSpecialFilter(String str) {
        String regEx = "[\\u00A0\\s\"`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        //将所有的特殊字符替换为空字符串
        return m.replaceAll("").trim();
    }

    public JDCookie getJDCookies(MyChromeClient myChromeClient) {
        JDCookie ck = new JDCookie();
        String mockCookie = System.getenv("mockCookie");
        if ("1".equals(mockCookie)) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            ck.setPtPin("PtPin");
            ck.setPtKey(uuid);
            return ck;
        }
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        if (webDriver != null) {
            String currentUrl = webDriver.getCurrentUrl();
            if (!currentUrl.startsWith("data:")) {
                Set<Cookie> cookies = webDriver.manage().getCookies();
                for (Cookie cookie : cookies) {
                    if ("pt_key".equals(cookie.getName())) {
                        ck.setPtKey(cookie.getValue());
                        break;
                    }
                }
                for (Cookie cookie : cookies) {
                    if ("pt_pin".equals(cookie.getName())) {
                        ck.setPtPin(cookie.getValue());
                        break;
                    }
                }
            }
        }
        return ck;
    }

    private JDScreenBean getScreenInner(MyChromeClient myChromeClient) throws IOException, InterruptedException {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());

        String screenBase64 = null;
        byte[] screen = null;
        if (CommonAttributes.debug || myChromeClient.getJdLoginType() == JDLoginType.qr) {
            //创建全屏截图
            screen = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            screenBase64 = Base64Utils.encodeToString(screen);
        }

        //是否空白页面
        String currentUrl = webDriver.getCurrentUrl();
        if (currentUrl.startsWith("data:")) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.EMPTY_URL);
        }

        //获取网页文字
        String pageText = null;
        try {
            pageText = webDriver.findElement(By.tagName("body")).getText();
        } catch (Exception e) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.EMPTY_URL);
        }

        WebElement element = null;
        if (pageText.contains("京东登录注册")) {
            element = webDriver.findElement(By.id("app"));
        }

        JDCookie jdCookies = getJDCookies(myChromeClient);
        if (!jdCookies.isEmpty()) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.SUCCESS_CK, jdCookies);
        }

        if (pageText.contains("输入的手机号未注册")) {
            boolean isChecked = webDriver.findElement(By.xpath("//input[@class='policy_tip-checkbox']")).isSelected();
            if (!isChecked) {
                return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.AGREE_AGREEMENT);
            }
        }

        if (pageText.contains("其他登录方式") && myChromeClient.getJdLoginType() == JDLoginType.qr) {
            webDriver.findElement(By.xpath("//a[@report-eventid=\"MLoginRegister_SMSQQLogin\"]")).click();
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.NORMAL);
        }

        if (myChromeClient.getJdLoginType() == JDLoginType.qr) {
            int retry = 1;
            while (pageText.contains("服务异常")) {
                log.info("尝试第" + retry + "次刷新");
                if (retry++ > 10) {
                    break;
                }
                webDriver.navigate().refresh();
                Thread.sleep(500);
                WebDriverUtil.waitForJStoLoad(webDriver);
                pageText = webDriver.findElement(By.tagName("body")).getText();
            }

            jdCookies = getJDCookies(myChromeClient);
            if (!jdCookies.isEmpty()) {
                return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.SUCCESS_CK, jdCookies);
            }

            if (pageText.contains("服务异常")) {
                return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.REQUIRE_REFRESH);
            }

            if (!pageText.contains("请使用QQ手机版扫描二维码") && webDriver.getCurrentUrl().contains("qq.com")) {
                webDriver.switchTo().frame("ptlogin_iframe");
                pageText = webDriver.findElement(By.tagName("body")).getText();
            }
            if (pageText.contains("二维码失效") && pageText.contains("请点击刷新")) {
                return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.REQUIRE_REFRESH);
            }
            if (pageText.contains("扫描成功")) {
                return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.WAIT_QR_CONFIRM);
            }

            if (pageText.contains("请选择认证方式进行认证")) {
                String text = webDriver.findElement(By.xpath("//p[@class='page-notice']")).getText();
                String phone = webDriver.findElement(By.xpath("//span[@class='text-account']")).getText();
                log.info(text);
                webDriver.findElement(By.xpath("//a[@class='mode-btn voice-mode']")).click();
                WebDriverUtil.waitForJStoLoad(webDriver);
                Thread.sleep(1000);
                webDriver.findElement(By.xpath("//button[@class='getMsg-btn timer active']")).click();
                Thread.sleep(1000);
                WebDriverUtil.waitForJStoLoad(webDriver);
                JDScreenBean jdScreenBean = new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.WAIT_CUBE_SMSCODE);
                jdScreenBean.setMsg("请输入手机号" + phone + "获取到的验证码！");
                return jdScreenBean;
            }
            WebElement qrElement = webDriver.findElement(By.xpath("//span[@class='qrlogin_img_out']"));
            if (qrElement != null) {
                screenBase64 = qrElement.getScreenshotAs(OutputType.BASE64);
            } else {
                screenBase64 = webDriver.getScreenshotAs(OutputType.BASE64);
            }
            return new JDScreenBean("", screenBase64, JDScreenBean.PageStatus.REQUIRE_SCANQR);
        }

        if (element == null && jdCookies.isEmpty()) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.EMPTY_URL);
        }

        if (pageText.contains("短信验证码发送次数")) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.VERIFY_CODE_MAX);
        }

        if (pageText.contains("短信验证码登录")) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.SWITCH_SMS_LOGIN);
        }

        WebElement loginBtn = webDriver.findElement(By.xpath("//a[@report-eventid='MLoginRegister_SMSLogin']"));
        HashSet<String> loginBtnClasses = new HashSet<>(Arrays.asList(loginBtn.getAttribute("class").split(" ")));
        WebElement sendAuthCodeBtn = webDriver.findElement(By.xpath("//button[@report-eventid='MLoginRegister_SMSReceiveCode']"));
        HashSet<String> sendAuthCodeBtnClasses = new HashSet<>(Arrays.asList(sendAuthCodeBtn.getAttribute("class").split(" ")));
        //登录按钮是否可点击
        boolean canClickLogin = loginBtnClasses.contains("btn-active");

        //获取验证码是否可点击
        boolean canSendAuth = sendAuthCodeBtnClasses.contains("active");
        int authCodeCountDown = -1;
        if (!canSendAuth) {
            String timerText = sendAuthCodeBtn.getText().trim();
            if ("获取验证码".equals(timerText)) {
                authCodeCountDown = 0;
            } else {
                String regex = "重新获取\\((\\d+)s\\)";
                Pattern compile = Pattern.compile(regex);
                Matcher matcher = compile.matcher(timerText);
                if (matcher.matches()) {
                    String group = matcher.group(1);
                    authCodeCountDown = Integer.parseInt(group);
                }
            }
        }

        WebElement chapter_element;
        //需要输入验证码
        if (pageText.contains("安全验证") && !pageText.contains("验证成功")) {
            //创建全屏截图
            chapter_element = webDriver.findElement(By.id("captcha_modal"));
            screenBase64 = chapter_element.getScreenshotAs(OutputType.BASE64);
            if (CommonAttributes.debug) {
                File screenshotAs = chapter_element.getScreenshotAs(OutputType.FILE);
                FileUtils.copyFile(screenshotAs, new File("/tmp/" + UUID.randomUUID() + ".png"));
            }
            CaptchaImg captchaImg = getCaptchaImg(myChromeClient);
            JDScreenBean jdScreenBean = new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.REQUIRE_VERIFY);
            jdScreenBean.setCaptchaImg(captchaImg);
            return jdScreenBean;
        }

        if (pageText.contains("验证码错误多次")) {
            return new JDScreenBean(screenBase64, "", JDScreenBean.PageStatus.VERIFY_FAILED_MAX);
        }

        Long expire = myChromeClient.getExpireSeconds();
        JDScreenBean.PageStatus status = JDScreenBean.PageStatus.NORMAL;
        if (canClickLogin) {
            status = JDScreenBean.PageStatus.SHOULD_CLICK_LOGIN;
        }
        if (canSendAuth) {
            status = JDScreenBean.PageStatus.SHOULD_SEND_AUTH;
        }
        JDScreenBean bean = new JDScreenBean(screenBase64, "", jdCookies, status, authCodeCountDown, canClickLogin, canSendAuth, expire, null, System.currentTimeMillis(), "", null);
        if (!jdCookies.isEmpty()) {
            bean.setPageStatus(JDScreenBean.PageStatus.SUCCESS_CK);
        }

        return bean;
    }

    public void crackCaptcha(MyChromeClient myChromeClient) throws IOException {
        String gaps = getGap(myChromeClient);
        if (gaps != null) {
            String uuid = gaps.split(",")[0];
            int gap = Integer.parseInt(gaps.split(",")[1]);
            RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
            WebElement slider = webDriver.findElement(By.xpath("//div[@class='sp_msg']/img"));
            SlideVerifyBlock.moveWay1(webDriver, slider, gap, uuid, CommonAttributes.debug);
        }
    }

    public String getGap(MyChromeClient myChromeClient) throws IOException {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        WebElement img_tips_wraper = webDriver.findElement(By.xpath("//div[@class='img_tips_wraper']"));
        if (!img_tips_wraper.isDisplayed()) {
            String cpc_img = webDriver.findElement(By.id("cpc_img")).getAttribute("src");
            String small_img = webDriver.findElement(By.id("small_img")).getAttribute("src");

            Matcher matcher = pattern.matcher(cpc_img);
            String bigImageBase64 = null;
            String smallImageBase64 = null;
            if (matcher.matches()) {
                bigImageBase64 = matcher.group(1);
            }
            matcher = pattern.matcher(small_img);
            if (matcher.matches()) {
                smallImageBase64 = matcher.group(1);
            }
            if (bigImageBase64 != null && smallImageBase64 != null) {
                byte[] bgBytes = Base64Utils.decodeFromString(bigImageBase64);
                byte[] bgSmallBytes = Base64Utils.decodeFromString(smallImageBase64);
                UUID uuid = UUID.randomUUID();
                ByteArrayInputStream in = new ByteArrayInputStream(bgBytes);
                ByteArrayInputStream inSmall = new ByteArrayInputStream(bgSmallBytes);
                BufferedImage image = ImageIO.read(in);
                BufferedImage imageSmall = ImageIO.read(inSmall);
                Mat mat = Java2DFrameUtils.toMat(image);
                Mat matSmall = Java2DFrameUtils.toMat(imageSmall);
                Rect rect = OpenCVUtil.getOffsetX(mat, matSmall, uuid.toString(), CommonAttributes.debug);
                return uuid + "," + rect.x();
            }
        }
        return null;
    }

    public boolean toJDlogin(MyChromeClient myChromeClient) {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        webDriver.manage().deleteAllCookies();
        webDriver.navigate().to("https://plogin.m.jd.com/login/login?appid=300&returnurl=https%3A%2F%2Fwq.jd.com%2Fpassport%2FLoginRedirect%3Fstate%3D1101624461975%26returnurl%3Dhttps%253A%252F%252Fhome.m.jd.com%252FmyJd%252Fnewhome.action%253Fsceneval%253D2%2526ufc%253D%2526&source=wq_passport");
        WebDriverUtil.waitForJStoLoad(webDriver);
        if (myChromeClient.getJdLoginType() == JDLoginType.qr) {
            CompletableFuture<Boolean> waitTask = CompletableFuture.supplyAsync(() -> {
                try {
                    JDScreenBean screenInner = getScreen(myChromeClient);
                    while (screenInner.getPageStatus() != JDScreenBean.PageStatus.NORMAL) {
                        screenInner = getScreen(myChromeClient);
                        Thread.sleep(200);
                    }
                    return true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }
            });
            try {
                Boolean res = waitTask.get(6, TimeUnit.SECONDS);
                return res != null && res;
            } catch (Exception e) {
                return false;
            }
        } else {
            return true;
        }
    }

    public void controlChrome(MyChromeClient myChromeClient, String currId, String currValue) {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        WebElement element = null;
        if ("phone".equals(currId)) {
            element = webDriver.findElement(By.xpath("//input[@type='tel']"));
        } else if ("sms_code".equals(currId)) {
            element = webDriver.findElement(By.id("authcode"));
        } else if ("cube_sms_code".equals(currId)) {
            element = webDriver.findElement(By.xpath("//input[@class='acc-input msgCode']"));
        }
        if (element != null) {
            boolean isOsMac = SystemUtils.IS_OS_MAC;
            if (isOsMac) {
                for (int i = 0; i < 20; i++) {
                    element.sendKeys(Keys.BACK_SPACE);
                }
            } else {
                element.sendKeys(Keys.CONTROL + "a");
            }
            element.sendKeys(currValue);
        }
        if ("cube_sms_code".equals(currId)) {
            webDriver.findElement(By.xpath("//a[@class='btn active']")).click();
        }
    }

    public boolean jdLogin(MyChromeClient myChromeClient) throws IOException, InterruptedException {
        boolean res = false;
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        JDScreenBean screen = getScreen(myChromeClient);
        if (screen.isCanClickLogin()) {
            WebDriverWait wait = new WebDriverWait(webDriver, 5);
            WebElement element = null;
            try {
                element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@report-eventid='MLoginRegister_SMSLogin']")));
                element.click();
                res = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        WebDriverUtil.waitForJStoLoad(webDriver);
        return res;
    }

    public void click(MyChromeClient myChromeClient, By xpath) {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        if (webDriver != null) {
            WebElement sureButton = webDriver.findElement(xpath);
            sureButton.click();
        }
    }

    public boolean sendAuthCode(MyChromeClient myChromeClient) throws IOException, InterruptedException {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        JDScreenBean screen = getScreen(myChromeClient);
        if (screen.isCanSendAuth()) {
            WebElement sendAuthCodeBtn = webDriver.findElement(By.xpath("//button[@report-eventid='MLoginRegister_SMSReceiveCode']"));
            sendAuthCodeBtn.click();
            return true;
        } else {
            return false;
        }
    }

    public JDScreenBean getScreen(MyChromeClient myChromeClient) {
        JDScreenBean bean = null;
        try {
            bean = getScreenInner(myChromeClient);
            if (bean.getPageStatus() == JDScreenBean.PageStatus.EMPTY_URL) {
                toJDlogin(myChromeClient);
//            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.REQUIRE_VERIFY) {
//                service.crackCaptcha();
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.VERIFY_FAILED_MAX) {
                click(myChromeClient, By.xpath("//div[@class='alert-sure']"));
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.VERIFY_CODE_MAX) {
                click(myChromeClient, By.xpath("//button[@class='dialog-sure']"));
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.SWITCH_SMS_LOGIN) {
                click(myChromeClient, By.xpath("//span[@report-eventid='MLoginRegister_SMSVerification']"));
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.AGREE_AGREEMENT) {
                click(myChromeClient, By.xpath("//input[@class='policy_tip-checkbox']"));
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.REQUIRE_REFRESH) {
                toJDlogin(myChromeClient);
            } else if (bean.getPageStatus() == JDScreenBean.PageStatus.WAIT_CUBE_SMSCODE) {
                System.out.println("等待用户输入验证码");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        Long expire = (myChromeClient.getExpireTime() - System.currentTimeMillis()) / 1000;
        bean.setSessionTimeOut(expire);
        bean.setStatClient(driverFactory.getStatClient());
        return bean;
    }

    public int getQLUploadDirectConfig() {
        String ql_upload_direct = System.getenv("QL_UPLOAD_DIRECT");
        int qlUploadDirect = 0;
        if (!StringUtils.isEmpty(ql_upload_direct)) {
            try {
                qlUploadDirect = Integer.parseInt(ql_upload_direct);
            } catch (NumberFormatException e) {
            }
        }
        if (driverFactory.getQlConfigs() != null && driverFactory.getQlConfigs().size() <= 1) {
            return 1;
        }
        return qlUploadDirect;
    }

    public JSONObject uploadQingLong(Set<Integer> chooseQLId, String phone, String remark, String ck, String chromeSessionId, int qlUploadDirect) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", 0);
        if ((chooseQLId != null && chooseQLId.size() > 0) || qlUploadDirect == 1) {
            List<QLUploadStatus> uploadStatuses = new ArrayList<>();
            if (driverFactory.getQlConfigs() != null) {
                for (QLConfig qlConfig : driverFactory.getQlConfigs()) {
                    if (qlUploadDirect == 1 || chooseQLId.contains(qlConfig.getId())) {
                        if (qlConfig.getQlLoginType() == QLConfig.QLLoginType.TOKEN) {
                            QLUploadStatus status = uploadQingLongWithToken(chromeSessionId, ck, phone, remark, qlConfig);
                            log.info("上传" + qlConfig.getQlUrl() + "结果" + status.getUploadStatus());
                            uploadStatuses.add(status);
                        }
                        if (qlConfig.getQlLoginType() == QLConfig.QLLoginType.USERNAME_PASSWORD) {
                            QLUploadStatus status = uploadQingLong(chromeSessionId, ck, phone, remark, qlConfig);
                            log.info("上传" + qlConfig.getQlUrl() + "结果" + status.getUploadStatus());
                            uploadStatuses.add(status);
                        }
                    }
                }
            }

            driverFactory.releaseWebDriver(chromeSessionId, false);

            if (qlUploadDirect != 1) {
                Map<String, Object> map = new HashMap<>();
                map.put("uploadStatuses", uploadStatuses);
                try {
                    Template template = freeMarkerConfigurer.getConfiguration().getTemplate("fragment/uploadRes.ftl");
                    String process = FreemarkerUtils.process(template, map);
                    log.debug(process);
                    jsonObject.put("html", process);
                    jsonObject.put("status", 1);
                } catch (IOException | TemplateException e) {
                    e.printStackTrace();
                }
            } else {
                StringBuilder errorMsg = new StringBuilder();
                StringBuilder successMsg = new StringBuilder();
                for (QLUploadStatus uploadStatus : uploadStatuses) {
                    String label = uploadStatus.getQlConfig().getLabel();
                    if (uploadStatus.getUploadStatus() <= 0) {
                        if (!StringUtils.isEmpty(label)) {
                            errorMsg.append(label);
                        } else {
                            errorMsg.append("QL_URL_").append(uploadStatus.getQlConfig().getId());
                        }
                        errorMsg.append("上传失败<br/>");
                    }
                    if (uploadStatus.isFull()) {
                        if (!StringUtils.isEmpty(label)) {
                            errorMsg.append(label);
                        } else {
                            errorMsg.append("QL_URL_").append(uploadStatus.getQlConfig().getId());
                        }
                        errorMsg.append("超容量了<br/>");
                    }
                    if (uploadStatus.getUploadStatus() > 0) {
                        if (!StringUtils.isEmpty(label)) {
                            successMsg.append(label);
                        } else {
                            successMsg.append("QL_URL_").append(uploadStatus.getQlConfig().getId());
                        }
                        successMsg.append("上传成功<br/>");
                    }
                }
                if (errorMsg.length() > 0) {
                    jsonObject.put("status", -2);
                    jsonObject.put("html", errorMsg.toString());
                    return jsonObject;
                }
                jsonObject.put("status", 2);
                String s = successMsg.toString();
                if (s.endsWith("<br/>")) {
                    s = s.substring(0, s.length() - 5);
                }
                jsonObject.put("html", s);
            }
        } else {
            jsonObject.put("status", 0);
        }
        return jsonObject;
    }

    public QLUploadStatus uploadQingLong(String chromeSessionId, String ck, String phone, String remark, QLConfig qlConfig) {
        int res = -1;
        if (qlConfig.getRemain() <= 0) {
            return new QLUploadStatus(qlConfig, res, qlConfig.getRemain() <= 0, "", "");
        }
        int maxRetry = 3;
        String token = null;
        while (true) {
            if (maxRetry == 0) {
                break;
            }
            token = getUserNamePasswordToken(driverFactory.getDriverBySessionId(chromeSessionId), qlConfig);
            if (token != null) {
                break;
            }
            maxRetry--;
        }
        if (token != null) {
            return uploadQingLongWithToken(chromeSessionId, ck, phone, remark, qlConfig);
        } else {
            res = 0;
        }
        return new QLUploadStatus(qlConfig, res, qlConfig.getRemain() <= 0, "", "");
    }

    private String getUserNamePasswordToken(RemoteWebDriver webDriver, QLConfig qlConfig) {
        try {
            webDriver.get(qlConfig.getQlUrl() + "/login");
            boolean b = WebDriverUtil.waitForJStoLoad(webDriver);
            if (b) {
                if (!webDriver.getCurrentUrl().endsWith("/login")) {
//                    new RemoteWebStorage(new RemoteExecuteMethod(webDriver)).getLocalStorage().clear();
                    String token = new RemoteWebStorage(new RemoteExecuteMethod(webDriver)).getLocalStorage().getItem("token");
                    if (token != null) {
                        return token;
                    }
                    webDriver.get(qlConfig.getQlUrl() + "/login");
                }
                webDriver.findElement(By.id("username")).sendKeys(qlConfig.getQlUsername());
                webDriver.findElement(By.id("password")).sendKeys(qlConfig.getQlPassword());
                webDriver.findElement(By.xpath("//button[@type='submit']")).click();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                b = WebDriverUtil.waitForJStoLoad(webDriver);
                if (b) {
                    RemoteExecuteMethod executeMethod = new RemoteExecuteMethod(webDriver);
                    RemoteWebStorage webStorage = new RemoteWebStorage(executeMethod);
                    LocalStorage storage = webStorage.getLocalStorage();
                    return storage.getItem("token");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public JSONArray getCurrentCKS(RemoteWebDriver webDriver, QLConfig qlConfig, String searchValue) {
        int maxRetry = 3;
        while (true) {
            maxRetry--;
            if (maxRetry == 0) {
                break;
            }
            if (qlConfig.getQlLoginType() == QLConfig.QLLoginType.USERNAME_PASSWORD) {
                String token = getUserNamePasswordToken(webDriver, qlConfig);
                qlConfig.setQlToken(new QLToken(token));
            }
            if (qlConfig.getQlToken() == null) {
                return null;
            }
            String url = qlConfig.getQlUrl() + "/" + (qlConfig.getQlLoginType() == QLConfig.QLLoginType.TOKEN ? "open" : "api") + "/envs?searchValue=" + searchValue + "&t=" + System.currentTimeMillis();
            log.info("开始获取当前ck数量" + url);
            HttpHeaders headers = getHttpHeaders(qlConfig);
            ResponseEntity<String> exchange = null;
            try {
                exchange = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                if (exchange.getStatusCode().is2xxSuccessful()) {
                    String body = exchange.getBody();
                    return JSON.parseObject(body).getJSONArray("data");
                } else if (exchange.getStatusCodeValue() == 401) {
                    log.info("token" + qlConfig.getQlToken().getToken() + "失效");
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                int rawStatusCode = e.getRawStatusCode();
                if (rawStatusCode == 401) {
                    continue;
                }
                e.printStackTrace();
            } catch (RestClientException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void fetchCurrentCKS_count(RemoteWebDriver driver, QLConfig qlConfig, String searchValue) {
        JSONArray currentCKS = getCurrentCKS(driver, qlConfig, searchValue);
        int ckSize = 0;
        if (currentCKS != null) {
            for (int i = 0; i < currentCKS.size(); i++) {
                JSONObject jo = currentCKS.getJSONObject(i);
                if ("JD_COOKIE".equals(jo.getString("name"))) {
                    ckSize++;
                }
            }
            log.info("获取到的ck数量=" + ckSize);
            qlConfig.setRemain(qlConfig.getCapacity() - ckSize);
        }
    }

    public QLUploadStatus uploadQingLongWithToken(String chromeSessionId, String ck, String phone, String remark, QLConfig qlConfig) {
        JDCookie jdCookie = JDCookie.parse(ck);
        int res = -1;
        String pushRes = "";
        String xddRes = "";

        boolean update = false;
        String updateId = "";
        String updateRemark = null;
        JSONArray data = getCurrentCKS(driverFactory.getDriverBySessionId(chromeSessionId), qlConfig, "");
        if (data != null && data.size() > 0) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject jsonObject = data.getJSONObject(i);
                String _id = jsonObject.getString("_id");
                String value = jsonObject.getString("value");
                String name = jsonObject.getString("name");
                if ("JD_COOKIE".equals(name)) {
                    JDCookie oldCookie = null;
                    try {
                        oldCookie = JDCookie.parse(value);
                        if (oldCookie.getPtPin().equals(jdCookie.getPtPin())) {
                            update = true;
                            updateId = _id;
                            updateRemark = jsonObject.getString("remarks");
                            break;
                        }
                    } catch (Exception e) {
                        log.warn(qlConfig.getQlUrl() + "后台有一条不能解析的ck");
                        e.printStackTrace();
                    }
                }
            }
        }

        HttpHeaders headers = getHttpHeaders(qlConfig);
        String url = qlConfig.getQlUrl() + "/" + (qlConfig.getQlLoginType() == QLConfig.QLLoginType.TOKEN ? "open" : "api") + "/envs?t=" + System.currentTimeMillis();
        if (!update) {
            if (qlConfig.getRemain() <= 0) {
                return new QLUploadStatus(qlConfig, res, qlConfig.getRemain() <= 0, pushRes, xddRes);
            }
            JSONArray jsonArray = new JSONArray();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("value", ck);
            jsonObject.put("name", "JD_COOKIE");
            String s = strSpecialFilter(remark);
            jsonObject.put("remarks", StringUtils.isEmpty(s) ? phone : remark);
            jsonArray.add(jsonObject);
            HttpEntity<?> request = new HttpEntity<>(jsonArray.toJSONString(), headers);
            log.info("开始上传ck " + url);

            int maxRetry = 3;
            while (true) {
                maxRetry--;
                if (maxRetry == 0) {
                    break;
                }
                ResponseEntity<String> exchange = null;
                try {
                    exchange = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                    if (exchange.getStatusCode().is2xxSuccessful()) {
                        log.info("create resp content : " + exchange.getBody() + ", resp code : " + exchange.getStatusCode());
                        pushRes = doNodeJSNotify("新的CK上传到" + qlConfig.getLabel(), remark.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                        res = 1;
                        break;
                    }
                } catch (HttpClientErrorException.Unauthorized e) {
                    int rawStatusCode = e.getRawStatusCode();
                    log.info(rawStatusCode + " : token" + qlConfig.getQlToken().getToken() + "失效");
                } catch (RestClientException e) {
                    e.printStackTrace();
                }

                if (qlConfig.getQlLoginType() == QLConfig.QLLoginType.USERNAME_PASSWORD) {
                    String token = getUserNamePasswordToken(driverFactory.getDriverBySessionId(chromeSessionId), qlConfig);
                    qlConfig.setQlToken(new QLToken(token));
                }
                if (qlConfig.getQlToken() == null) {
                    res = 0;
                }
            }
        } else {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("value", ck);
            jsonObject.put("name", "JD_COOKIE");
            String s = strSpecialFilter(remark);
            String remarksRes = remark;
            //如果上传的备注是空
            if (StringUtils.isEmpty(s)) {
//                老的备注也是空，则备注填手机号
                if (StringUtils.isEmpty(updateRemark)) {
                    remarksRes = phone;
                } else {
                    remarksRes = updateRemark;
                }
            }
            jsonObject.put("remarks", remarksRes);
            jsonObject.put("_id", updateId);
            HttpEntity<?> request = new HttpEntity<>(jsonObject.toJSONString(), headers);
            log.info("开始更新ck" + url);
            ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            if (exchange.getStatusCode().is2xxSuccessful()) {
                log.info("update resp content : " + exchange.getBody() + ", resp code : " + exchange.getStatusCode());
                String body = exchange.getBody();
                JSONObject result = JSON.parseObject(body).getJSONObject("data");
                String enableStatus = "";
                log.info("ck状态" + result.getString("status"));
                if (result.getIntValue("status") == 1) {
                    log.info("开始启用ck" + updateId);
                    JSONArray enableBody = new JSONArray();
                    enableBody.add(updateId);
                    HttpEntity<?> enableRequest = new HttpEntity<>(enableBody.toJSONString(), headers);
                    String enableUrl = qlConfig.getQlUrl() + "/" + (qlConfig.getQlLoginType() == QLConfig.QLLoginType.TOKEN ? "open" : "api") + "/envs/enable?t=" + System.currentTimeMillis();
                    ResponseEntity<String> enableExchange = restTemplate.exchange(enableUrl, HttpMethod.PUT, enableRequest, String.class);
                    if (enableExchange.getStatusCode().is2xxSuccessful()) {
                        log.info("enableCookie resp content : " + enableExchange.getBody() + ", resp code : " + enableExchange.getStatusCode());
                        enableStatus = "并启用";
                    }
                }
                try {
                    pushRes = doNodeJSNotify("更新" + enableStatus + "老的CK到" + qlConfig.getLabel(), remarksRes + ":" + phone);
                    log.info("pushRes = " + pushRes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                res = 1;
            }
        }
        return new QLUploadStatus(qlConfig, res, qlConfig.getRemain() <= 0, pushRes, xddRes);
    }

    public String doXDDNotify(String ck) {
        String xddUrl = driverFactory.getXddUrl();
        String xddToken = driverFactory.getXddToken();
        if (!StringUtils.isEmpty(xddUrl) && !StringUtils.isEmpty(xddToken)) {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("ck", ck);
            paramMap.put("token", xddToken);
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(paramMap, headers);
//            ResponseEntity<String> exchange = restTemplate.exchange(xddUrl, HttpMethod.POST, request, String.class);
//            if (exchange.getStatusCode().is2xxSuccessful()) {
//                return exchange.getBody();
//            }
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "*/*");
            headers.put("Accept-Encoding", "gzip, deflate, br");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");
            headers.put("Connection", "keep-alive");
            headers.put("DNT", "1");
            headers.put("sec-ch-ua", "\"Google Chrome\";v=\"89\", \" Not;A Brand\";v=\"99\", \"Chromium\";v=\"89\"");
            headers.put("sec-ch-ua-mobile", "?0");
            headers.put("sec-ch-ua-platform", "\"macOS\"");
            headers.put("Sec-Fetch-Dest", "empty");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Site", "same-origin");
            headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4577.82 Safari/537.36");
            return httpClientUtil.doPost(xddUrl, paramMap, headers);
        }
        return null;
    }

    private synchronized String doNodeJSNotify(String title, String content) {
        log.info("doNodeJSNotify title = " + title + " content = " + content);
        Properties properties = driverFactory.getProperties();
        StringBuilder sb = new StringBuilder();
        if (properties != null) {
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (NODEJS_PUSH_KEYS.contains(key)) {
                    sb.append(key).append("=").append(value).append(" ");
                }
            }
        }
        sb.append(" /opt/bin/notify ").append(title).append(" ").append(content);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", sb.toString());
        log.info("executing : " + sb);
        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitVal = process.waitFor();
            if (exitVal == 0) {
                System.out.println("Success!");
                return output.toString();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return "";
    }

    private HttpHeaders getHttpHeaders(QLConfig qlConfig) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + qlConfig.getQlToken().getToken());
        headers.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4577.63 Safari/537.36");
        headers.add("Content-Type", "application/json;charset=UTF-8");
        headers.add("Accept-Encoding", "gzip, deflate");
        headers.add("Accept-Language", "zh-CN,zh;q=0.9");
        return headers;
    }

    public void fetchNewOpenIdToken(QLConfig qlConfig) {
        getToken(qlConfig);
    }

    @Override
    public void run(String... args) throws MalformedURLException {
        initQLConfig();
        List<QLConfig> qlConfigs = driverFactory.getQlConfigs();
        if (qlConfigs.isEmpty()) {
            log.warn("请配置至少一个青龙面板地址! 否则获取到的ck无法上传");
        }
        log.info("启动成功! ");
        initSuccess = true;
        try {
            String s = IOUtils.toString(Objects.requireNonNull(OpenCVUtil.class.getClassLoader().getResourceAsStream("mock_captcha_points.txt")), StandardCharsets.UTF_8);
            String[] split = s.split("\n");
            for (String line : split) {
                String[] s1 = line.split(" ");
                if (s1.length == 2) {
                    int gap = Integer.parseInt(s1[0]);
                    String[] points = s1[1].split("\\|");
                    List<Point> pointList = new ArrayList<>();
                    for (String point : points) {
                        String[] split1 = point.split(",");
                        if (split1.length == 3) {
                            long time = Long.parseLong(split1[0]);
                            int x = Integer.parseInt(split1[1]);
                            int y = Integer.parseInt(split1[2]);
                            pointList.add(new Point(x, y, time));
                        }
                    }
                    List<List<Point>> old = MOCK_CAPTCHA_POINTS_MAP.get(gap);
                    if (old == null) {
                        old = new ArrayList<>();
                    }
                    old.add(pointList);
                    MOCK_CAPTCHA_POINTS_MAP.put(gap, old);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void initQLConfig() {
        List<QLConfig> qlConfigs = driverFactory.getQlConfigs();
        Map<String, MyChrome> chromes = driverFactory.getChromes();
        Iterator<QLConfig> iterator = qlConfigs.iterator();
        RemoteWebDriver driver = null;
        try {
            for (MyChrome chrome : chromes.values()) {
                if (chrome.getUserTrackId() == null) {
                    driver = chrome.getWebDriver();
                    break;
                }
            }
            while (iterator.hasNext()) {
                QLConfig qlConfig = iterator.next();
                if (StringUtils.isEmpty(qlConfig.getLabel())) {
                    qlConfig.setLabel("请配置QL_LABEL_" + qlConfig.getId() + "");
                }

                boolean verify1 = !StringUtils.isEmpty(qlConfig.getQlUrl());
                boolean verify2 = verify1 && !StringUtils.isEmpty(qlConfig.getQlUsername()) && !StringUtils.isEmpty(qlConfig.getQlPassword());
                boolean verify3 = verify1 && !StringUtils.isEmpty(qlConfig.getQlClientID()) && !StringUtils.isEmpty(qlConfig.getQlClientSecret());

                boolean result_token = false;
                boolean result_usernamepassword = false;
                if (verify3) {
                    boolean success = getToken(qlConfig);
                    if (success) {
                        result_token = true;
                        qlConfig.setQlLoginType(QLConfig.QLLoginType.TOKEN);
                        fetchCurrentCKS_count(driver, qlConfig, "");
                    } else {
                        log.warn(qlConfig.getQlUrl() + "获取token失败，获取到的ck无法上传，已忽略");
                    }
                } else if (verify2) {
                    boolean result = false;
                    try {
                        result = driverFactory.initInnerQingLong(driver, qlConfig);
                        if (result) {
                            qlConfig.setQlLoginType(QLConfig.QLLoginType.USERNAME_PASSWORD);
                            fetchCurrentCKS_count(driver, qlConfig, "");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (result) {
                        result_usernamepassword = true;
                    } else {
                        log.info("初始化青龙面板" + qlConfig.getQlUrl() + "登录失败, 获取到的ck无法上传，已忽略");
                    }
                }

                if (!result_token && !result_usernamepassword) {
                    iterator.remove();
                }
            }
        } finally {
            if (driver != null && driver.getSessionId() != null) {
                driverFactory.releaseWebDriver(driver.getSessionId().toString(), false);
            }
        }

        log.info("成功添加" + qlConfigs.size() + "套配置");
    }

    public boolean getToken(QLConfig qlConfig) {
        String qlUrl = qlConfig.getQlUrl();
        String qlClientID = qlConfig.getQlClientID();
        String qlClientSecret = qlConfig.getQlClientSecret();
        try {
            ResponseEntity<String> entity = restTemplate.getForEntity(qlUrl + "/open/auth/token?client_id=" + qlClientID + "&client_secret=" + qlClientSecret, String.class);
            if (entity.getStatusCodeValue() == 200) {
                String body = entity.getBody();
                log.info("获取token " + body);
                JSONObject jsonObject = JSON.parseObject(body);
                Integer code = jsonObject.getInteger("code");
                if (code == 200) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    String token = data.getString("token");
                    String tokenType = data.getString("token_type");
                    long expiration = data.getLong("expiration");
                    qlConfig.setQlToken(new QLToken(token, tokenType, expiration));
                    return true;
                }
            }
        } catch (Exception e) {
            log.error(qlUrl + "获取token失败，请检查配置");
        }
        return false;
    }

    public boolean isInitSuccess() {
        return initSuccess;
    }

    public CaptchaImg getCaptchaImg(MyChromeClient myChromeClient) {
        String bigImageBase64 = null;
        String smallImageBase64 = null;
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        if (webDriver != null) {
            WebElement img_tips_wraper = webDriver.findElement(By.xpath("//div[@class='img_tips_wraper']"));
            if (!img_tips_wraper.isDisplayed()) {
                String cpc_img = webDriver.findElement(By.id("cpc_img")).getAttribute("src");
                String small_img = webDriver.findElement(By.id("small_img")).getAttribute("src");

                Matcher matcher = pattern.matcher(cpc_img);
                if (matcher.matches()) {
                    bigImageBase64 = matcher.group(1);
                }
                matcher = pattern.matcher(small_img);
                if (matcher.matches()) {
                    smallImageBase64 = matcher.group(1);
                }
            }
        }
        return new CaptchaImg(bigImageBase64, smallImageBase64);
    }

    public boolean manualCrackCaptcha(MyChromeClient myChromeClient, List<Point> pointList) {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        if (webDriver != null) {
            WebElement img_tips_wraper = webDriver.findElement(By.xpath("//div[@class='img_tips_wraper']"));
            if (!img_tips_wraper.isDisplayed()) {
                String cpc_img = webDriver.findElement(By.id("cpc_img")).getAttribute("src");
                String small_img = webDriver.findElement(By.id("small_img")).getAttribute("src");
                Matcher matcher = pattern.matcher(cpc_img);
                String bigImageBase64 = null;
                String smallImageBase64 = null;
                if (matcher.matches()) {
                    bigImageBase64 = matcher.group(1);
                }
                matcher = pattern.matcher(small_img);
                if (matcher.matches()) {
                    smallImageBase64 = matcher.group(1);
                }
                if (bigImageBase64 != null && smallImageBase64 != null) {
                    byte[] bgBytes = Base64Utils.decodeFromString(bigImageBase64);
                    byte[] bgSmallBytes = Base64Utils.decodeFromString(smallImageBase64);
                    UUID uuid = UUID.randomUUID();
                    ByteArrayInputStream in = new ByteArrayInputStream(bgBytes);
                    ByteArrayInputStream inSmall = new ByteArrayInputStream(bgSmallBytes);
                    Rect rect = null;
                    WebElement slider = null;
                    try {
                        BufferedImage image = ImageIO.read(in);
                        BufferedImage imageSmall = ImageIO.read(inSmall);
                        Mat mat = Java2DFrameUtils.toMat(image);
                        Mat matSmall = Java2DFrameUtils.toMat(imageSmall);
                        rect = OpenCVUtil.getOffsetX(mat, matSmall, uuid.toString(), CommonAttributes.debug);
                        slider = webDriver.findElement(By.xpath("//div[@class='sp_msg']/img"));
                        if (pointList == null || pointList.size() == 0) {
                            List<List<Point>> mockList = MOCK_CAPTCHA_POINTS_MAP.get(rect.x());
                            if (CollectionUtils.isEmpty(mockList)) {
                                return false;
                            }
                            int n = new Random().nextInt(mockList.size());
                            pointList = mockList.get(n);
                        }
                        SlideVerifyBlock.manualWay(webDriver, slider, rect.x(), pointList);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return true;
    }

    public void manualCrackCaptchaMock(MyChromeClient myChromeClient, List<Point> pointList) {
        RemoteWebDriver webDriver = driverFactory.getDriverBySessionId(myChromeClient.getChromeSessionId());
        if (webDriver != null) {
//            Point point = pointList.get(pointList.size() - 1);
//            int mockGap = point.getX();
            try {
                String gaps = getGap(myChromeClient);
                int mockGap = Integer.parseInt(gaps.split(",")[1]);
                File file = new File("mock_captcha_points.txt");
                if (file.exists()) {
                    List<String> strings = FileUtils.readLines(file, "utf-8");
                    Set<Integer> ranges = new HashSet<>();
                    for (String s : strings) {
                        String s1 = s.split(" ")[0];
                        ranges.add(Integer.parseInt(s1));
                    }
                    System.out.println(ranges.size());
                }
                FileUtils.writeStringToFile(file, mockGap + " " + JSON.toJSONString(pointList) + "\n", "utf-8", true);
                WebElement element = webDriver.findElement(By.xpath("//img[@class='jcap_refresh']"));
                element.click();
                boolean displayed = webDriver.findElement(By.xpath("//div[@class='img_loading_refreshTips']")).isDisplayed();
                int max = 5;
                while (displayed) {
                    Thread.sleep(100);
                    displayed = webDriver.findElement(By.xpath("//div[@class='img_loading_refreshTips']")).isDisplayed();
                    max--;
                    if (max <= 0) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
