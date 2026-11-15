public abstract class User {
    private final String username;
    private final String name;
    private final String password;

    protected User(String username, String name, String password) {
        this.username = requireText(username, "Username");
        this.name = requireText(name, "Name");

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public boolean matchesPassword(String candidate) {
        return password.equals(candidate);
    }

    // Used only by the text-file storage class.
    String passwordForStorage() {
        return password;
    }

    public abstract String getRole();

    @Override
    public String toString() {
        return getRole() + " | " + username + " | " + name;
    }

    protected static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}

