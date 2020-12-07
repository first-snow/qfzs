package work.cxlm.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import work.cxlm.cache.lock.CacheLock;
import work.cxlm.exception.ForbiddenException;
import work.cxlm.model.dto.TimePeriodSimpleDTO;
import work.cxlm.model.entity.Room;
import work.cxlm.model.entity.TimePeriod;
import work.cxlm.model.entity.User;
import work.cxlm.model.entity.support.TimeIdGenerator;
import work.cxlm.model.properties.RuntimeProperties;
import work.cxlm.model.vo.TimeTableVO;
import work.cxlm.repository.TimeRepository;
import work.cxlm.security.context.SecurityContextHolder;
import work.cxlm.service.*;
import work.cxlm.service.base.AbstractCrudService;
import work.cxlm.utils.DateUtils;
import work.cxlm.utils.ServiceUtils;

import java.util.*;

import static work.cxlm.model.enums.TimeState.*;

/**
 * @author beizi
 * @author Chiru
 * <p>
 * create: 2020-11-20 12:52
 */
@Slf4j
@Service
public class TimeServiceImpl extends AbstractCrudService<TimePeriod, Long> implements TimeService {

    private RoomService roomService;
    private UserService userService;
    private TimeService timeService;
    private BelongService belongService;


    private final TimeRepository timeRepository;
    private final OptionService optionService;

    @Autowired
    public void setBelongService(BelongService belongService) {
        this.belongService = belongService;
    }

    @Autowired
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    @Autowired
    public void setRoomService(RoomService roomService) {
        this.roomService = roomService;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }


    public TimeServiceImpl(TimeRepository timeRepository,
                           OptionService optionService) {
        super(timeRepository);
        this.timeRepository = timeRepository;
        this.optionService = optionService;
    }

    // ***************** Private ***********************************

    // 获得指定周、活动室的全部时间段
    private List<TimePeriod> getWeekTimePeriods(@NonNull Integer roomId, @NonNull Integer week) {
        Assert.notNull(roomId, "roomId 不能为 null");
        Assert.notNull(week, "请求的周次不能为 null");

        DateUtils du = new DateUtils(new Date()).weekStart().changeWeek(week + 1);
        Long endTimeId = TimeIdGenerator.encodeId(du, roomId);
        Long startTimeId = TimeIdGenerator.encodeId(du.changeWeek(-1), roomId);
        return timeRepository.findAllByRoomIdAndIdBetween(roomId, startTimeId, endTimeId);
    }

    //******************* Override ******************************************

    @Override
    public TimeTableVO getTimeTable(@NonNull Integer roomId, @NonNull Integer week) {
        Room targetRoom = roomService.getById(roomId);
        User nowUser = SecurityContextHolder.ensureUser();

        // 获得活动室指定周的全部时段
        List<TimePeriod> weekTimePeriods = getWeekTimePeriods(roomId, week);

        // 整理数据，并标记自己占用的时间段
        Map<Long, TimePeriod> timePeriodMap = ServiceUtils.convertToMap(weekTimePeriods, TimePeriod::getId, time -> {
            if (Objects.equals(time.getUserId(), nowUser.getId())) {
                time.setState(MINE);
            }
            return time;
        });
        LinkedList<List<TimePeriodSimpleDTO>> timeTable = new LinkedList<>();
        LinkedList<String> timeTitleList = new LinkedList<>();
        int startHour = targetRoom.getStartHour();
        int endHour = targetRoom.getEndHour();

        DateUtils du = new DateUtils(new Date());
        long nowTimeId = TimeIdGenerator.encodeId(du, 0);  // 获得当前时间点的 ID
        du.changeWeek(week).weekStart();  // 移动到指定周周一
        for (int i = startHour; i < endHour; i++) {
            // 生成行标题
            du.setHour(i + 1);
            String endTimeTitle = du.generateTitleTitle();
            String startTimeTitle = du.setHour(i).generateTitleTitle();
            timeTitleList.add(String.format("%s %2s", startTimeTitle, endTimeTitle));

            // 行数据整理
            LinkedList<TimePeriodSimpleDTO> hourRow = new LinkedList<>();
            for (int j = 0; j < 7; j++) {
                Long timeId = TimeIdGenerator.encodeId(du, roomId);

                // 数据库中存在该 ID，将其放入时间表格
                if (timePeriodMap.containsKey(timeId)) {
                    hourRow.add(new TimePeriodSimpleDTO().convertFrom(timePeriodMap.get(timeId)));
                } else {

                    // 数据库中不存在，生成占位时间段实例
                    TimePeriod emptyTime = new TimePeriod(timeId, du.get());
                    // 状态变更
                    if(nowTimeId > timeId) {
                        emptyTime.setState(PASSED);
                    } else if (week > 0) {
                        emptyTime.setState(NOT_OPEN);
                    } else {
                        emptyTime.setState(IDLE);
                    }
                    emptyTime.setShowText("");
                    hourRow.add(new TimePeriodSimpleDTO().convertFrom(emptyTime));
                }
                du.tomorrow();  // 下移一天
            }
            timeTable.add(hourRow);
            du.changeWeek(-1);  // 前移一周
        }

        // 返回值构建
        Date weekNumberStart = new Date(optionService.getByProperty(RuntimeProperties.WEEK_START_DATE, Long.class).
                orElse(Long.valueOf(RuntimeProperties.WEEK_START_DATE.defaultValue())));
        TimeTableVO res = new TimeTableVO();
        res.setTimeTable(timeTable);
        res.setTimeTitle(timeTitleList);
        res.setWeek(DateUtils.weekNumberOf(weekNumberStart, du.get()));
        return res;
    }

    @Override
    @CacheLock(prefix = "time_dis_lock", expired = 0, msg = "因为操作冲突，您的请求被取消取消，请重试", argSuffix = "timeId")
    public void occupyTimePeriod(@NonNull Long timeId) {
        Assert.notNull(timeId, "timeId 不能为 null");
        // 得到目标时段实体
        TimePeriod timeInDB = getByIdOfNullable(timeId);
        if (timeInDB == null) {
            timeInDB = new TimePeriod(timeId);
        }

        // 得到活动室
        Integer roomId = (int) (timeId % 10000);
        Room targetRoom = roomService.getById(roomId);

        // 校验用户权限
        User nowUser = SecurityContextHolder.ensureUser();
        if (!nowUser.getRole().isSystemAdmin() &&
                !roomService.roomAvailableToUser(targetRoom, nowUser)) {
            throw new ForbiddenException("您的权限不足，无法对该活动室进行操作");
        }

        // 校验时段是否合法（可预约，没过期，已开放）
        Date targetDate = TimeIdGenerator.decodeIdToDate(timeId);
        Date now = new Date();
        if (targetDate.before(now)) {
            throw new ForbiddenException("您无法改写历史");
        } else if (DateUtils.weekStartOf(targetDate).after(now)) {
            throw new ForbiddenException("该时段尚未开放预订");
        }

        // 检验是否超出了时长限制
        List<TimePeriod> weekTimePeriods = getWeekTimePeriods((int) (timeId % 10000), 0);
        int[] statistic = new int[8];  // 统计，0 为周占用，1~7 为周日到周六
        weekTimePeriods.forEach(time -> {
            if (!Objects.equals(time.getUserId(), nowUser.getId())) {
                return;
            }
            statistic[0]++;  // 周统计
            statistic[DateUtils.whatDayIs(time.getStartTime())]++;  // 日统计
        });
        if (statistic[0] >= targetRoom.getWeekLimit() ||
                statistic[DateUtils.whatDayIs(timeInDB.getStartTime())] >= targetRoom.getDayLimit()) {
            throw new ForbiddenException("超出限定时长：周限定: [" + targetRoom.getWeekLimit() + "]，日限定: [" + targetRoom.getDayLimit() + "]");
        }

        // 签到继承
        Long previousTimeId = timeId - 100_0000L;
        TimePeriod previousTime = getByIdOfNullable(previousTimeId);
        if (previousTime != null && // 前移时段不为 null
                Objects.equals(previousTime.getUserId(), nowUser.getId()) && // 前一时段同为当前用户占用
                previousTime.getSigned()) { // 前一时段已签到
            timeInDB.setSigned(true); // 本时段自动设为已签到状态
        }

        // 占用时段
        timeInDB.setShowText(nowUser.getRealName());
        timeInDB.setRoomId(roomId);
        timeInDB.setUserId(nowUser.getId());
        timeInDB.setState(OCCUPIED);
        // 存储、应用修改（更新或新建）
        timeRepository.save(timeInDB);
    }

    @Override
    public void cancelTimePeriod(@NonNull Long timeId) {
        Assert.notNull(timeId, "timeId 不能为 null");
        TimePeriod target = getById(timeId);
        User nowUser = SecurityContextHolder.ensureUser();
        // 权限校验：只能解除自己的预约
        if (!Objects.equals(target.getUserId(), nowUser.getId()) && !nowUser.getRole().isSystemAdmin()) {
            throw new ForbiddenException("您没有权限取消该时段的预约");
        }
        // 从数据库中移除
        remove(target);
    }

}
