package ma.aquaops.notifications;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    // Thread-safe : le listener Kafka et le test tournent dans des threads différents
    private final List<String> notifications = new CopyOnWriteArrayList<>();

    public void addNotification(String message) {
        notifications.add(message);
    }

    public List<String> getNotifications() {
        return notifications;
    }
}
