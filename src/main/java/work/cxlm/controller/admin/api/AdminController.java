package work.cxlm.controller.admin.api;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import work.cxlm.cache.lock.CacheLock;
import work.cxlm.model.dto.UserDTO;
import work.cxlm.model.params.LoginParam;
import work.cxlm.model.params.AuthorityParam;
import work.cxlm.model.params.UserParam;
import work.cxlm.model.vo.DashboardVO;
import work.cxlm.model.vo.PageUserVO;
import work.cxlm.security.token.AuthToken;
import work.cxlm.service.AdminService;
import work.cxlm.service.UserService;

import javax.validation.Valid;

/**
 * created 2020/10/21 14:31
 *
 * @author cxlm
 */
@RestController
@RequestMapping("/key3/admin/api/")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("login")
    @ApiOperation("管理员登录")
    @CacheLock(prefix = "admin_token")
    public AuthToken authLogin(@RequestBody @Valid LoginParam loginParam) {
        return adminService.authenticate(loginParam);
    }

    @GetMapping("logout")
    @ApiOperation("登出")
    @CacheLock(prefix = "admin_token")
    public void logout() {
        adminService.clearToken();
    }

    @PostMapping("refresh/{refreshToken}")
    @ApiOperation("刷新登录凭证")
    @CacheLock(prefix = "admin_token")
    public AuthToken refresh(@PathVariable("refreshToken") String refreshToken) {
        return adminService.refreshToken(refreshToken);
    }

    @PostMapping("authority")
    @ApiOperation("为指定学号的用户授权，授权的角色、管理的社团需要通过参数指定")
    public void grant(@Valid @RequestBody AuthorityParam param) {
        adminService.grant(param);
    }

    @PutMapping("authority")
    @ApiOperation("收回指定学号的用户授权，授权的角色、管理的社团需要通过参数指定")
    public void revoke(@Valid @RequestBody AuthorityParam param) {
        adminService.revoke(param);
    }

    @GetMapping("dashboard/{clubId:\\d+}")
    @ApiOperation("请求仪表盘页面需要的数据")
    public DashboardVO getDashboardData(@PathVariable("clubId") Integer clubId) {
        return adminService.dashboardDataOf(clubId);
    }

}
