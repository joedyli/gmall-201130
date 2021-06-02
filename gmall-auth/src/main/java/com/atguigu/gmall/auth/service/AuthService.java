package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.UserException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Service
public class AuthService {

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private JwtProperties properties;

    public void login(String loginName, String password, HttpServletRequest request, HttpServletResponse response) {
        // 1.调用远程接口，查询用户信息
        ResponseVo<UserEntity> userEntityResponseVo =
                this.umsClient.queryUser(loginName, password);
        UserEntity userEntity = userEntityResponseVo.getData();

        // 2.判断是否为空，为空则直接登录失败
        if (userEntity == null){
            throw new UserException("您输入的用户名或者密码错误！");
        }

        // 3.组装载荷Map
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userEntity.getId());
        map.put("username", userEntity.getUsername());
        // 防止token被盗用，添加用户的ip地址
        String ip = IpUtils.getIpAddressAtService(request);
        map.put("ip", ip);

        try {
            // 4.制作jwt类型的token
            String token = JwtUtils.generateToken(map, this.properties.getPrivateKey(), this.properties.getExpire());

            // 5.放入cookie
            CookieUtils.setCookie(request, response, this.properties.getCookieName(), token, this.properties.getExpire() * 60);
            // 6.为了方便展示登录用户信息，需要把昵称放入cookie中
            CookieUtils.setCookie(request, response, this.properties.getUnick(), userEntity.getNickname(), this.properties.getExpire() * 60);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
