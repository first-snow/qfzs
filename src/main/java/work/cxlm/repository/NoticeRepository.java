package work.cxlm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import work.cxlm.model.entity.Notice;

/**
 * created 2020/12/10 15:01
 *
 * @author Chiru
 */
public interface NoticeRepository extends BaseRepository<Notice, Long> {

    /**
     * 查询某用户的全部通知
     *
     * @param id       指定用户 id
     * @param pageable 分页参数
     * @return 某用户的全部通知分页数据集
     */
    @NonNull
    Page<Notice> findAllByTargetUserId(Integer id, @NonNull Pageable pageable);
}
