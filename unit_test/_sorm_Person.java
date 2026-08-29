import com.zoffcc.applications.sorm.*;

@Table
public class Person
{
    @PrimaryKey(autoincrement = true)
    public long id;
    
    @Column
    public String name;
    
    @Column
    public String address;
    
    @Column
    public int social_number;
}
