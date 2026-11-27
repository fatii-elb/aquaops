package ma.aquaops.notifications;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCriticalIncidentEmail(int incidentId, int zoneId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("superviseur@aquaops.local");
        message.setSubject("[AquaOps] Alerte : Incident critique n°" + incidentId);
        message.setText("Un incident critique a été détecté dans la zone " + zoneId + ".\nIncident ID : " + incidentId);
        
        try {
            mailSender.send(message);
            System.out.println("E-mail envoyé pour l'incident critique " + incidentId);
        } catch (Exception e) {
            System.err.println("Impossible d'envoyer l'e-mail : " + e.getMessage());
        }
    }
}
