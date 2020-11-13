package work.cxlm.service.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import work.cxlm.cache.AbstractStringCacheStore;
import work.cxlm.event.logger.LogEvent;
import work.cxlm.exception.BadRequestException;
import work.cxlm.exception.NotFoundException;
import work.cxlm.exception.ServiceException;
import work.cxlm.mail.MailService;
import work.cxlm.model.dto.StatisticDTO;
import work.cxlm.model.entity.User;
import work.cxlm.model.enums.LogType;
import work.cxlm.model.params.LoginParam;
import work.cxlm.model.params.ResetPasswordParam;
import work.cxlm.security.authentication.Authentication;
import work.cxlm.security.context.SecurityContextHandler;
import work.cxlm.security.token.AuthToken;
import work.cxlm.security.util.SecurityUtils;
import work.cxlm.service.AdminService;
import work.cxlm.service.UserService;

import java.util.concurrent.TimeUnit;

/**
 * created 2020/10/21 15:40
 *
 * @author johnniang
 * @author ryanwang
 * @author cxlm
 * TODO IMPLEMENT THIS
 */
@Service
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final AbstractStringCacheStore cacheStore;
    private final MailService mailService;

    public AdminServiceImpl(UserService userService,
                            ApplicationEventPublisher eventPublisher,
                            AbstractStringCacheStore cacheStore,
                            MailService mailService) {
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.cacheStore = cacheStore;
        this.mailService = mailService;
    }

    @Override
    public User authenticate(LoginParam loginParam) {
        Assert.notNull(loginParam, "输入参数不能为 null");

        String username = loginParam.getUsername();

        String mismatchTip = "用户名或者密码不正确";

        final User user;

        try {
            // 通过用户名或邮箱获取用户
            user = Validator.isEmail(username) ?
                    userService.getByEmailOfNonNull(username) :
                    userService.getByUsernameOfNonNull(username);
        } catch (NotFoundException e) {
            log.error("用户名不存在： " + username);
            eventPublisher.publishEvent(new LogEvent(this, loginParam.getUsername(), LogType.LOGGED_FAILED, loginParam.getUsername()));

            throw new BadRequestException(mismatchTip);
        }

        userService.mustNotExpire(user);

        if (!userService.passwordMatch(user, loginParam.getPassword())) {
            // 密码不匹配
            eventPublisher.publishEvent(new LogEvent(this, loginParam.getUsername(), LogType.LOGGED_FAILED, loginParam.getUsername()));

            throw new BadRequestException(mismatchTip);
        }

        return user;
    }

    @Override
    public void clearToken() {
        Authentication authentication = SecurityContextHandler.getContext().getAuthentication();  // 获取登录凭证

        if (authentication == null) {
            throw new BadRequestException("您尚未登录，无法注销");
        }

        // 获取当前登录的用户
        User user = authentication.getUserDetail().getUser();

        // 清除 Token
        cacheStore.getAny(SecurityUtils.buildAccessTokenKey(user), String.class).ifPresent(accessToken -> {
            cacheStore.delete(SecurityUtils.buildAccessTokenKey(user));
            cacheStore.delete(SecurityUtils.buildAccessTokenKey(accessToken));
        });
        cacheStore.getAny(SecurityUtils.buildRefreshTokenKey(user), String.class).ifPresent(refreshToken -> {
            cacheStore.delete(SecurityUtils.buildRefreshTokenKey(user));
            cacheStore.delete(SecurityUtils.buildRefreshTokenKey(refreshToken));
        });

        // 注销事件
        eventPublisher.publishEvent(new LogEvent(this, user.getUsername(), LogType.LOGGED_OUT, "用户登出：" + user.getNickname()));

        log.info("用户登出");
    }

    @Override
    public void sendResetPasswordCode(ResetPasswordParam param) {
        cacheStore.getAny("code", String.class).ifPresent(code -> {
            throw new ServiceException("已经获取过验证码，请查收或者稍后再试");
        });

        // 生成四位随机数
        String code = RandomUtil.randomNumbers(8);
        log.info("获取了重设密码验证码：[{}]", code);

        // 设置验证码有效时长
        cacheStore.putAny("code", code, 5, TimeUnit.MINUTES);

        String content = "您正在进行重置密码操作，如果不是本人操作，请尽快应对。重置验证码为【" + code + "】，五分钟有效";
        mailService.sendTextMail(param.getEmail(), "MyFont：找回密码", content);
    }

    @Override
    public void resetPasswordByCode(ResetPasswordParam param) {
        // TODO: HERE
    }

    @Override
    public StatisticDTO getCount() {
        return null;
    }

    @Override
    public AuthToken refreshToken(String refreshToken) {
        return null;
    }
}
