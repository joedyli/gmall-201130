package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import io.jsonwebtoken.Jwt;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
@Data
public class LoginInterceptor implements HandlerInterceptor {

    //private Long userId;
    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Autowired
    private JwtProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("这是前置方法。。。");
        // TODO: 拦截完成
        //this.userId = 1l;
        //request.setAttribute("userId", 1l);

        // 从cookie中获取userKey 和 token
        String token = CookieUtils.getCookieValue(request, this.properties.getCookieName());
        String userKey = CookieUtils.getCookieValue(request, this.properties.getUserKey());
        if (StringUtils.isBlank(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.properties.getUserKey(), userKey, this.properties.getExpire());
        }

        UserInfo userInfo = new UserInfo(null, userKey);

        try {
            // 如果token不为空，解析token
            if (StringUtils.isNotBlank(token)){
                Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
                userInfo.setUserId(Long.valueOf(map.get("userId").toString()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        THREAD_LOCAL.set(userInfo);
        return true;
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
//        System.out.println("这是后置方法");
//    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 必须显式的调用remove方法，因为使用的是tomcat线程池，请求结束，线程并没有结束。以避免内存泄漏
        THREAD_LOCAL.remove();
    }
}
