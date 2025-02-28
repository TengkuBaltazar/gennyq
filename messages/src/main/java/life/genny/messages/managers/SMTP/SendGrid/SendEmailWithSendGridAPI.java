package life.genny.messages.managers.SMTP.SendGrid;

import life.genny.messages.managers.QEmailMessageManager;
import life.genny.qwandaq.utils.HttpUtils;
import org.jboss.logging.Logger;

import javax.json.JsonObject;
import java.net.http.HttpResponse;

public class SendEmailWithSendGridAPI {
    private JsonObject mail;
    private String apiKey;
    private String path;

    private static final Logger log = Logger.getLogger(QEmailMessageManager.class);

    public SendEmailWithSendGridAPI(JsonObject mail, String apiKey, String path) {
        this.mail = mail;
        this.apiKey = apiKey;
        this.path = path;
    }

    public void sendRequest() {
        log.info("Sending email with SendGrid API");

        // TODO: Fetch from env
        try {
            String requestBody = mail.toString();
//            System.out.println("####### requestBody: " + requestBody);

            // TODO: Proper response logging
            HttpResponse<String> httpResponse = HttpUtils.post(path, requestBody, apiKey);
            if (httpResponse != null) {
                int statusCode = httpResponse.statusCode();
                System.out.println("####### response: " + httpResponse.body());
                System.out.println("####### statusCode: " + statusCode);
            } else {
                System.out.println("####### Sendgrid NULL!");
            }
        } catch (Exception ex) {
            System.out.println("Exception: " + ex.getMessage());
        }
    }
}
