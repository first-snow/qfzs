package work.cxlm.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import work.cxlm.event.LogEvent;
import work.cxlm.exception.ForbiddenException;
import work.cxlm.model.dto.ClubDTO;
import work.cxlm.model.dto.RoomDTO;
import work.cxlm.model.entity.*;
import work.cxlm.model.enums.LogType;
import work.cxlm.model.params.ClubParam;
import work.cxlm.model.support.CreateCheck;
import work.cxlm.model.support.UpdateCheck;
import work.cxlm.model.vo.ClubRoomMapVO;
import work.cxlm.repository.ClubRepository;
import work.cxlm.security.context.SecurityContextHolder;
import work.cxlm.service.*;
import work.cxlm.service.base.AbstractCrudService;
import work.cxlm.utils.ServiceUtils;
import work.cxlm.utils.ValidationUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * created 2020/11/21 15:24
 *
 * @author Chiru
 */
@Service
@Slf4j
public class ClubServiceImpl extends AbstractCrudService<Club, Integer> implements ClubService {

    // ------------------ Autowired -------------------------

    private UserService userService;
    private JoiningService joiningService;
    private BillService billService;
    private RoomService roomService;
    private BelongService belongService;
    private AnnouncementService announcementService;

    private final ApplicationEventPublisher eventPublisher;

    public ClubServiceImpl(ClubRepository repository,
                           ApplicationEventPublisher eventPublisher) {
        super(repository);
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setJoiningService(JoiningService joiningService) {
        this.joiningService = joiningService;
    }

    @Autowired
    public void setBillService(BillService billService) {
        this.billService = billService;
    }

    @Autowired
    public void setRoomService(RoomService roomService) {
        this.roomService = roomService;
    }

    @Autowired
    public void setBelongService(BelongService belongService) {
        this.belongService = belongService;
    }

    @Autowired
    public void setAnnouncementService(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    // --------------------------- Override ---------------------------------------

    @Override
    public ClubDTO getManagedClubInfo(Integer clubId) {
        Assert.notNull(clubId, "社团 ID 不能为 null");
        User admin = SecurityContextHolder.ensureUser();
        Club targetClub = getById(clubId);
        if (!userService.managerOfClub(admin, targetClub)) {
            throw new ForbiddenException("权限不足，禁止查看");
        }
        return new ClubDTO().convertFrom(targetClub);
    }

    @Override
    public ClubDTO newClubByParam(ClubParam clubParam) {
        Assert.notNull(clubParam, "ClubParam 不能为 null");
        ValidationUtils.validate(clubParam, CreateCheck.class);

        User admin = SecurityContextHolder.ensureUser();
        if (!admin.getRole().isSystemAdmin()) {
            throw new ForbiddenException("只有系统管理员可以执行本操作");
        }
        Club newClub = create(clubParam.convertTo());
        eventPublisher.publishEvent(new LogEvent(this, admin.getId(), LogType.NEW_CLUB,
                "创建了新的社团：" + newClub.getName()));
        return new ClubDTO().convertFrom(newClub);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClubDTO updateByParam(ClubParam clubParam) {
        Assert.notNull(clubParam, "ClubParam 不能为 null");
        ValidationUtils.validate(clubParam, UpdateCheck.class);

        User admin = SecurityContextHolder.ensureUser();
        if (admin.getRole().isNormalRole()) {
            throw new ForbiddenException("权限不足，禁止操作");
        }

        Club targetClub = getById(clubParam.getClubId());
        if (!userService.managerOfClub(admin, targetClub)) {
            throw new ForbiddenException("权限不足，禁止操作");
        }
        // 改动前后均为 0 的情况需要跳过判断，对该逻辑进行单独判断，不同直接使用 equals
        boolean allZero = (clubParam.getAssets() == null || clubParam.getAssets().compareTo(BigDecimal.ZERO) == 0) &&
                targetClub.getAssets().compareTo(BigDecimal.ZERO) == 0;
        if (!allZero && !Objects.equals(clubParam.getAssets(), targetClub.getAssets())) {
            BigDecimal assetChange = clubParam.getAssets().subtract(targetClub.getAssets());
            Bill newBill = new Bill();
            newBill.setAuthorId(admin.getId());
            newBill.setClubId(targetClub.getId());
            newBill.setCost(assetChange);
            newBill.setInfo("管理员" + admin.getRealName() + "手动调整");
            billService.create(newBill);
        }
        clubParam.update(targetClub);
        update(targetClub);
        return new ClubDTO().convertFrom(targetClub);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClub(Integer clubId) {
        Assert.notNull(clubId, "clubId 不能为 null");

        User admin = SecurityContextHolder.ensureUser();
        if (!admin.getRole().isSystemAdmin()) {
            throw new ForbiddenException("权限不足，禁止操作");
        }
        // 删除用户加入社团的信息
        joiningService.removeByIdClubId(clubId);
        // 删除社团财务信息
        billService.removeByClubId(clubId);
        announcementService.deleteClubAllAnnouncements(clubId);
        belongService.deleteClubRooms(clubId);
        // 删除社团
        removeById(clubId);
        eventPublisher.publishEvent(new LogEvent(this, admin.getId(), LogType.DELETE_CLUB,
                "删除了社团：" + clubId));
    }

    @Override
    public Map<Integer, Club> getAllClubMap() {
        List<Club> allClubs = listAll();
        return ServiceUtils.convertToMap(allClubs, Club::getId);
    }

    @Override
    public List<ClubDTO> listUserClubs() {
        User nowUser = SecurityContextHolder.ensureUser();
        List<Joining> userJoiningClubs = joiningService.listAllJoiningByUserId(nowUser.getId());
        Map<Integer, Club> allClubMap = getAllClubMap();
        return ServiceUtils.convertList(userJoiningClubs,
                joining -> new ClubDTO().convertFrom(allClubMap.get(joining.getId().getClubId())));
    }

    @Override
    public ClubRoomMapVO buildClubRoomMapOfUser() {
        ClubRoomMapVO res = new ClubRoomMapVO();
        List<ClubDTO> userClubs = listUserClubs();
        Map<Integer, ClubDTO> clubDtoMap = ServiceUtils.convertToMap(userClubs, ClubDTO::getId);
        res.setClubs(userClubs);
        Map<Integer, Room> allRoomMap = roomService.getAllRoomMap();
        HashMap<Integer, List<RoomDTO>> clubRooms = new HashMap<>(4);
        belongService.listAll().forEach(belong -> {
            Integer clubId = belong.getId().getClubId();
            if (clubDtoMap.containsKey(clubId)) {
                List<RoomDTO> targetList;
                if (clubRooms.containsKey(clubId)) {
                    targetList = clubRooms.get(clubId);
                } else {
                    targetList = new LinkedList<>();
                    clubRooms.put(clubId, targetList);
                }
                targetList.add(new RoomDTO().convertFrom(allRoomMap.get(belong.getId().getRoomId())));
            }
        });
        res.setClubRooms(clubRooms);
        return res;
    }
}
