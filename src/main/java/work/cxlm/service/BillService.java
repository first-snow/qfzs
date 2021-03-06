package work.cxlm.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import work.cxlm.model.dto.BillDTO;
import work.cxlm.model.entity.Bill;
import work.cxlm.model.params.BillParam;
import work.cxlm.model.vo.BillTableVO;
import work.cxlm.model.vo.BillVO;
import work.cxlm.service.base.CrudService;

/**
 * created 2020/11/26 14:38
 *
 * @author Chiru
 */
public interface BillService extends CrudService<Bill, Integer> {

    /**
     * 列出某社团前 top 条收支
     *
     * @param top      条目数
     * @param clubId   社团 ID
     * @param showHead 是否显示用户相关信息
     * @return 通过分页查询得到最新的 top 条收支
     */
    Page<BillDTO> pageClubLatest(int top, Integer clubId, boolean showHead);

    /**
     * 删除社团全部收支记录
     *
     * @param clubId 目标社团 id
     */
    void removeByClubId(Integer clubId);

    /**
     * 列出某社团全部收支（前端分页）
     *
     * @param clubId 社团 ID
     * @return DataTable 需要的数据
     */
    BillTableVO listClubAllBill(Integer clubId);

    /**
     * 通过表单创建或更新收支实体，param 中有 ID 时判定为更新，否则为新建
     *
     * @param param 表单对象
     * @return 补充了当前可用经费字段的 BillDTO 实例（BillVO）
     */
    @Transactional(rollbackFor = Exception.class)
    BillVO saveBillBy(@NonNull BillParam param);

    /**
     * 通过 ID 删除收支项
     *
     * @param billId 收支 ID
     * @return 补充了当前可用经费字段的 BillDTO 实例（BillVO）
     */
    @Transactional(rollbackFor = Exception.class)
    BillVO deleteBill(Integer billId);

    /**
     * 分页获取社团账单
     *
     * @param clubId   目标社团 id
     * @param pageable 分页参数
     * @return Page 包装的 BillDTO 结果集
     */
    Page<BillDTO> pageClubBills(Integer clubId, Pageable pageable);
}
