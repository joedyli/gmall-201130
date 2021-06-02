package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
//import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "D:\\project-1130\\rsa\\rsa.pub";
    private static final String priKeyPath = "D:\\project-1130\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 3);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MjI1MTYzNjB9.crV5-oHtnsL4ZuX-gKznPE7ig4oVh9lFGPEqr8I3r-SCqby0fuVDqjnvGuIxKKLP_RYMGw8Qtd9X0Hail5wyV3e5FsihlASHhLDolaiOhRS8v60tZ4oa-IEVHPnQ6teEK0PpIidx4d0IdM6vRlcWShxEVK2su67CLlmaVV1aapM7YQfYtwiwNTxwqj_kBw5T32wzE2NzAIukolMbt-1m8zQKwSq9d88EJ-yvwcBz8vPLf7MBxV5FedRet5NFh09IloWfiA6L7NwvViPpfjngkIKl8uHuLrdIoVraMd5YrkGbd9Hfpt9bZaOhmQtQC4cosLISnPk-KZRtpQ2lSxAFmA";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}