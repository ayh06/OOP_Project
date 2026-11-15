public class Student extends User {
    private final String email;
    private final String phone;

    public Student(String universityId, String name, String email,
                   String phone, String password) {
        super(universityId, name, password);
        this.email = requireText(email, "Email");
        this.phone = requireText(phone, "Phone");
    }

    public String getUniversityId() {
        return getUsername();
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public String getRole() {
        return "Student";
    }
}
