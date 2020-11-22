package work.cxlm.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.util.StringUtils;
import work.cxlm.model.enums.UserGender;
import work.cxlm.model.enums.UserRole;
import work.cxlm.model.support.QfzsConst;

import javax.persistence.*;

/**
 * 用户实体类
 * created 2020/10/21 15:03
 *
 * @author cxlm
 */
@Data
@Entity
@Table(name = "users", uniqueConstraints = { // 唯一键约束（改为BIGINT，作为索引会更快一些），学号必须唯一
        @UniqueConstraint(name = "uni_stu_no", columnNames = "student_no")
})
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "custom-id")
    @GenericGenerator(name = "custom-id", strategy = "work.cxlm.model.entity.support.CustomIdGenerator")
    private Integer id;

    /**
     * 微信 ID，openId
     */
    @Column(name = "wx_id", length = 30)
    private String wxId;

    /**
     * 微信名
     */
    @Column(name = "wx_name", length = 100)
    private String wxName;

    /**
     * 学工号
     */
    @Column(name = "student_no", length = 15)
    private Long studentNo;

    /**
     * 学院名
     */
    @Column(name = "institute", length = 50)
    private String institute;

    /**
     * 专业名
     */
    @Column(name = "major", length = 60)
    private String major;

    /**
     * 入学年份
     */
    @Column(name = "enroll_year")
    private Integer enrollYear;

    /**
     * 真实姓名
     */
    @Column(name = "real_name", length = 30)
    private String realName;

    /**
     * 用户性别
     */
    @Column(name = "gender")
    @ColumnDefault("0")
    private UserGender gender;

    /**
     * 用户头像
     */
    @Column(name = "head")
    private String head;

    /**
     * 个性签名
     */
    @Column(name = "sign")
    private String sign;

    /**
     * 用户邮箱
     */
    @Column(name = "email", length = 60)
    private String email;

    /**
     * 用户角色
     */
    @Column(name = "role")
    @ColumnDefault("0")
    private UserRole role;

    @Override
    public void prePersist() {
        super.prePersist();
        if (email == null) {
            email = "";
        }
        if (role == null) {
            role = UserRole.NORMAL;
        }
        if (StringUtils.isEmpty(sign)) {
            sign = QfzsConst.DEFAULT_USER_SIGNATURE;
        }
    }

}
