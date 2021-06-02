package com.atguigu.gmall.ums.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.Query;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;
import org.springframework.util.CollectionUtils;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public Boolean checkData(String data, Integer type) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();

        switch (type) {
            case 1: wrapper.eq("username", data); break;
            case 2: wrapper.eq("phone", data); break;
            case 3: wrapper.eq("email", data); break;
            default:
                return null;
        }

        return this.count(wrapper) == 0;
    }

    @Override
    public void register(UserEntity userEntity, String code) {
        // TODO：1.校验验证码 查询redis和用户的code比较

        // 2. 生成盐
        String uuid = UUID.randomUUID().toString();
        String salt = StringUtils.substring(uuid, 0, 6);
        userEntity.setSalt(salt);

        // 3. 加盐加密
        userEntity.setPassword(DigestUtils.md5Hex(userEntity.getPassword() + salt));

        // 4. 保存到数据库
        userEntity.setLevelId(1l);
        userEntity.setSourceType(1);
        userEntity.setIntegration(1000);
        userEntity.setGrowth(1000);
        userEntity.setStatus(1);
        userEntity.setCreateTime(new Date());
        userEntity.setNickname(userEntity.getUsername());
        this.save(userEntity);

        // TODO: 删除redis中验证码
    }

    @Override
    public UserEntity queryUser(String loginName, String password) {
        // 先根据登录名查询用户
        List<UserEntity> userEntities = this.list(new QueryWrapper<UserEntity>()
                .eq("username", loginName)
                .or().eq("phone", loginName)
                .or().eq("email", loginName));

        // 判断用户是否为空
        if (CollectionUtils.isEmpty(userEntities)){
            return null;
        }

        for (UserEntity userEntity : userEntities) {
            String pwd = password;
            // 获取到用户中盐
            String salt = userEntity.getSalt();

            // 对用户输入的密码加盐加密
            pwd = DigestUtils.md5Hex(pwd + salt);

            // 和数据库中密文密码比较，不一致输错
            if (StringUtils.equals(pwd, userEntity.getPassword())){
                return userEntity;
            }
        }

        return null;
    }

}