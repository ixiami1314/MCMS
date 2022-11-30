/**
 * Copyright (c) 2012-present 铭软科技(mingsoft.net)
 * 本软件及相关文档文件（以下简称“软件”）的版权归 铭软科技 所有
 * 遵循 铭软科技《服务协议》中的《保密条款》
 */








package net.mingsoft.basic.filter;

import net.mingsoft.basic.aop.FileVerifyAop;
import net.mingsoft.basic.exception.BusinessException;
import net.mingsoft.basic.util.SpringUtil;
import net.mingsoft.base.util.SqlInjectionUtil;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * XSS 过滤器 用于请求参数的脚本数据
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private HttpServletRequest request = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(XssHttpServletRequestWrapper.class);

    /**
     * 配置可以通过过滤的白名单
     */
    private static final Whitelist whitelist = new Whitelist();
    /**
     * 配置过滤化参数,不对代码进行格式化
     */
    private static final Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String line = null;
        StringBuffer result = new StringBuffer();
        while ((line = br.readLine()) != null) {
            result.append(line);
        }
        return new WrappedServletInputStream(new ByteArrayInputStream(clean(result.toString()).getBytes()));
    }

    /**
     * 覆盖getParameter方法，将参数名和参数值都做xss过滤。
     *
     * 如果需要获得原始的值，则通过super.getParameterValues(name)来获取
     *
     * getParameterNames,getParameterValues和getParameterMap也可能需要覆盖
     */
    @Override
    public String getParameter(String name) {
        if (("content".equals(name) || name.endsWith("WithHtml"))) {
            return super.getParameter(name);
        }
        name = clean(name);
        String value = super.getParameter(name);
        if (StringUtils.isNotBlank(value)) {
            value = clean(value);
        }
        return value;
    }

    @Override
    public Map getParameterMap() {
        Map map = super.getParameterMap();
        // 返回值Map
        Map<String, String> returnMap = new HashMap<String, String>();
        Iterator entries = map.entrySet().iterator();
        Map.Entry entry;
        String name = "";
        String value = "";
        while (entries.hasNext()) {
            entry = (Map.Entry) entries.next();
            name = (String) entry.getKey();
            Object valueObj = entry.getValue();
            if (null == valueObj) {
                value = "";
            } else if (valueObj instanceof String[]) {
                String[] values = (String[]) valueObj;
                for (int i = 0; i < values.length; i++) {
                    value = values[i] + ",";
                }
                value = value.substring(0, value.length() - 1);
            } else {
                value = valueObj.toString();
            }
            returnMap.put(name, clean(name, value).trim());
        }
        return returnMap;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] arr = super.getParameterValues(name);
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                arr[i] = clean(arr[i]);
            }
        }
        return arr;
    }

    /**
     * 覆盖getHeader方法，将参数名和参数值都做xss过滤。
     *
     * 如果需要获得原始的值，则通过super.getHeaders(name)来获取
     *
     * getHeaderNames 也可能需要覆盖
     */
    @Override
    public String getHeader(String name) {
        name = clean(name);
        String value = super.getHeader(name);
        if (StringUtils.isNotBlank(value)) {
            value = clean(value);
        }
        return value;
    }

    /**
     * 获取最原始的request
     *
     * @return
     */
    @Override
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * 获取最原始的request的静态方法
     *
     * @return
     */
    public static HttpServletRequest getOrgRequest(HttpServletRequest req) {
        if (req instanceof XssHttpServletRequestWrapper) {
            return ((XssHttpServletRequestWrapper) req).getRequest();
        }

        return req;
    }

    public  String clean(String content) {
//        String result = Jsoup.clean(content, "", whitelist, outputSettings);
//        // 转义回来,防止&被转义导致结果误差
//        result = Parser.unescapeEntities(result, true);
//        // 请求头不做SQL注入检测
//        if (!content.equals(result)){
//            String uri = SpringUtil.getRequest().getRequestURI();
//            LOGGER.error("接口{}的参数不符合XSS规则{}",uri,content);
//            throw new BusinessException("参数异常,url:" + uri);
//        }
        return content;
    }

    public String clean(String name, String content) {
//        String result = Jsoup.clean(content, "", whitelist, outputSettings);
//        // 转义回来,防止&被转义导致结果误差
//        result = Parser.unescapeEntities(result, true);
//        if (!content.equals(result) || !SqlInjectionUtil.isSqlValid(content)){
//            String uri = SpringUtil.getRequest().getRequestURI();
//            LOGGER.error("接口不符合XSS规则:{}",uri);
//            LOGGER.error("参数名:{} 参数值:{}", name,content);
//            throw new BusinessException("参数异常,url:" + uri);
//        }
        return content;
    }

    private class WrappedServletInputStream extends ServletInputStream {
        public void setStream(InputStream stream) {
            this.stream = stream;
        }

        private InputStream stream;

        public WrappedServletInputStream(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        @Override
        public boolean isFinished() {
            return true;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {

        }
    }
}
