package work.cxlm.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import work.cxlm.cache.AbstractStringCacheStore;
import work.cxlm.event.logger.LogEvent;
import work.cxlm.exception.BadRequestException;
import work.cxlm.exception.ForbiddenException;
import work.cxlm.exception.NotFoundException;
import work.cxlm.model.dto.UserDTO;
import work.cxlm.model.entity.Club;
import work.cxlm.model.entity.User;
import work.cxlm.model.enums.LogType;
import work.cxlm.model.enums.UserRole;
import work.cxlm.model.params.LoginParam;
import work.cxlm.model.params.AuthorityParam;
import work.cxlm.model.params.UserParam;
import work.cxlm.model.support.QfzsConst;
import work.cxlm.repository.UserRepository;
import work.cxlm.security.authentication.Authentication;
import work.cxlm.security.context.SecurityContextHolder;
import work.cxlm.security.token.AuthToken;
import work.cxlm.security.util.SecurityUtils;
import work.cxlm.service.AdminService;
import work.cxlm.service.ClubService;
import work.cxlm.service.JoiningService;
import work.cxlm.service.UserService;
import work.cxlm.utils.ServiceUtils;
import work.cxlm.utils.ServletUtils;

import java.util.Objects;

/**
 * created 2020/11/21 14:01
 *
 * @author Chiru
 */
@Service
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserService userService;
    private final JoiningService joiningService;
    private AbstractStringCacheStore cacheStore;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ClubService clubService;

    public AdminServiceImpl(UserService userService,
                            AbstractStringCacheStore cacheStore,
                            UserRepository userRepository,
                            ApplicationEventPublisher eventPublisher,
                            ClubService clubService,
                            JoiningService joiningService) {
        this.userService = userService;
        this.cacheStore = cacheStore;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.clubService = clubService;
        this.joiningService = joiningService;
    }

    @Override
    @NonNull
    public AuthToken authenticate(@NonNull LoginParam loginParam) {
        User targetUser = userRepository.findByStudentNo(loginParam.getStudentNo())
                .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (!targetUser.getRole().isAdminRole()) {
            throw new ForbiddenException("您的权限不足，无法访问");
        }
        String passcodeCacheKey = QfzsConst.ADMIN_PASSCODE_PREFIX + targetUser.getId();
        String requirePasscode = cacheStore.getAny(passcodeCacheKey, String.class).
                orElseThrow(() -> new ForbiddenException("请使用小程序中生成的合法登录口令登录"));
        if (!Objects.equals(loginParam.getPasscode(), requirePasscode)) {
            eventPublisher.publishEvent(new LogEvent(this, targetUser.getRealName(), LogType.LOGGED_FAILED, ServletUtils.getRequestIp()));
            throw new ForbiddenException("错误的登录口令");
        }
        cacheStore.delete(passcodeCacheKey);  // 清除用完的 passcode key
        eventPublisher.publishEvent(new LogEvent(this, targetUser.getRealName(), LogType.LOGGED_IN, ServletUtils.getRequestIp()));
        return userService.buildAuthToken(targetUser, User::getId);
    }

    @Override
    public void clearToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BadRequestException("未登录，如何注销？");
        }

        User admin = authentication.getUserDetail().getUser();

        // Clear access token
        cacheStore.getAny(SecurityUtils.buildAccessTokenKey(admin), String.class).ifPresent(accessToken -> {
            // Delete token
            cacheStore.delete(SecurityUtils.buildAccessTokenKey(accessToken));
            cacheStore.delete(SecurityUtils.buildAccessTokenKey(admin));
        });

        // Clear refresh token
        cacheStore.getAny(SecurityUtils.buildRefreshTokenKey(admin), String.class).ifPresent(refreshToken -> {
            cacheStore.delete(SecurityUtils.buildRefreshTokenKey(refreshToken));
            cacheStore.delete(SecurityUtils.buildRefreshTokenKey(admin));
        });
        eventPublisher.publishEvent(new LogEvent(this, admin.getRealName(), LogType.LOGGED_OUT, ServletUtils.getRequestIp()));
    }

    @Override
    @NonNull
    public AuthToken refreshToken(@NonNull String refreshToken) {
        Assert.notNull(refreshToken, "refresh token 为空，无法刷新登录凭证");
        return userService.refreshToken(refreshToken, User::getId);
    }

    @Override
    @Transactional
    public void grant(AuthorityParam param) {
        changeAuthority(param, true);
    }

    @Override
    @Transactional
    public void revoke(AuthorityParam param) {
        changeAuthority(param, false);
    }

    private void changeAuthority(AuthorityParam param, boolean grant) {
        User admin = SecurityContextHolder.getCurrentUser().orElseThrow(() -> new ForbiddenException("未登录"));
        User targetUser = userRepository.findByStudentNo(param.getStudentNo()).orElseThrow(
                () -> new ForbiddenException("查询不到该用户信息，请核对后重试"));
        Club targetClub = clubService.getById(param.getClubId());
        if (admin.getRole() == UserRole.CLUB_ADMIN) {
            if (param.isSystemAdmin()) {
                throw new BadRequestException("权限不足，宁无法授权系统管理员");
            }
            if (userService.managerOf(admin.getId(), targetClub)) {
                throw new BadRequestException("宁不是该社团的管理员，不要瞎搞");
            }
        }
        // 系统管理员授权
        if (param.isSystemAdmin()) {
            targetUser.setRole(grant ? UserRole.SYSTEM_ADMIN : UserRole.NORMAL);
            userService.update(targetUser);
            eventPublisher.publishEvent(new LogEvent(this, admin.getRealName(), LogType.AUTH_REVOKE,
                    admin.getRealName() + "改变了" + param.getStudentNo() + "系统管理员权限为" + grant));
            return;
        }
        // 社团管理员授权
        if (grant) {
            joiningService.joinIfAbsent(targetUser.getId(), targetClub.getId(), true);
        } else {
            joiningService.revokeAdmin(targetUser.getId(), param.getClubId());
        }
        eventPublisher.publishEvent(new LogEvent(this, admin.getRealName(), LogType.AUTH_REVOKE,
                admin.getRealName() + "修改了" + param.getStudentNo() + "的" + targetClub.getName() + "社团管理员权限为" + grant));
    }

    @Override
    public void updateBy(@NonNull UserParam userParam) {
        Assert.notNull(userParam, "user param 不能为 null");
        User admin = SecurityContextHolder.ensureUser();
        User targetUser = userRepository.findByStudentNo(userParam.getStudentNo())
                .orElseThrow(() -> new NotFoundException("不存在该用户"));
        // 系统管理员、合法的社团管理员、自己
        boolean myself;
        if ((myself = Objects.equals(admin.getId(), targetUser.getId())) ||
                admin.getRole() == UserRole.SYSTEM_ADMIN ||
                userService.managerOf(admin, targetUser)) {
            if (myself) {
                targetUser = admin;
            }
            userParam.update(targetUser);
            userService.update(targetUser);
            return;
        }
        throw new ForbiddenException("您的权限不足，无法修改该用户信息");
    }

    @Override
    public void createBy(@NonNull UserParam userParam) {
        Assert.notNull(userParam, "user param 不能为 null");

        User admin = SecurityContextHolder.ensureUser();
        if (!admin.getRole().isAdminRole()) {
            log.error("没有管理员权限的用户发送了合法的管理员请求");
            throw new ForbiddenException("您的权限不足，无法创建用户信息");
        }
    }

    @Override
    @Transactional
    public void delete(@NonNull Integer userId) {
        Assert.notNull(userId, "user param 不能为 null");

        User admin = SecurityContextHolder.ensureUser();
        if (!admin.getRole().isAdminRole()) {
            throw new ForbiddenException("删除用户只能由系统管理员完成");
        }
        userRepository.deleteById(userId);
        joiningService.deleteByUserId(userId);
        // TODO：删除预定相关信息
    }

    @Override
    public Page<UserDTO> pageUsers(@NonNull Pageable pageable) {
        Page<User> userPage = userRepository.findAllBy(pageable);
        return ServiceUtils.convertPageElements(userPage, pageable, user -> new UserDTO().convertFrom(user));
    }
}
