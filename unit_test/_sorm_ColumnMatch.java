import com.zoffcc.applications.sorm.*;

@Table
public class ColumnMatch
{
    @PrimaryKey(autoincrement = true)
    public long id;

    @Column
    public String AB;

    @Column
    public String ABC;

    @Column
    public String ABCD;

    @Column
    public int AB_int;

    @Column
    public int ABC_int;
}
