import java.io.IOException;

public interface StateStorage {
    void save(ApplicationState state) throws IOException;
    ApplicationState load() throws IOException;
}
