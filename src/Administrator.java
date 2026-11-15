public class Administrator extends User {

    public Administrator() {
        super("admin", "Administrator", "gpu2026");
    }

    @Override
    public String getRole() {
        return "Administrator";
    }
}

