package domain;

public class Atm {

    private String name;
    private String password;

    public Atm(){

    }
    @Override
    public String toString() {
        return "Atm{" +
                "name='" + name + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

    public Atm(String name, String password) {
        this.name = name;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
