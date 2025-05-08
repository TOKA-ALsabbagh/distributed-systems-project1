package common;
import java.io.Serializable;

public class User implements Serializable {
    private String username;
    private String role; // manager, employee
    private String department;

    public User(String username, String role, String department) {
        this.username = username;
        this.role = role;
        this.department = department;
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getDepartment() { return department; }
}
