import com.zoffcc.applications.sorm.*;

@Table
public class BoolTest
{
    @PrimaryKey(autoincrement = true)
    public long id;

    @Column
    public String label;

    @Column
    public boolean is_active;

    @Column
    public boolean is_deleted;

    @Column
    public boolean has_permission;

    @Column
    public int priority;
}
