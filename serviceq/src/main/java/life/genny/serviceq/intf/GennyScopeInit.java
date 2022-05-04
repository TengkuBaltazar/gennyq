package life.genny.serviceq.intf;

import io.quarkus.arc.Arc;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.jboss.logging.Logger;

import life.genny.qwandaq.models.UserToken;

/**
 * Custom Genny Scope Initializer class for initializing the UserToken after consuming from Kafka.
 **/
@ApplicationScoped
public class GennyScopeInit {

	static final Logger log = Logger.getLogger(GennyScopeInit.class);

	Jsonb jsonb = JsonbBuilder.create();

	@Inject
	UserToken userToken;

	/**
	 * Default Constructor.
	 **/
	public GennyScopeInit() { }

	public void init(String data) { 

		// activate request scope and fetch UserToken
		Arc.container().requestContext().activate();

		if (data == null) {
			log.error("Null data received at Scope Init");
			return;
		}

		try {

			JsonObject json = jsonb.fromJson(data, JsonObject.class);
			String token = json.getString("token");

			// init GennyToken from token string
			userToken.init(token);
			log.debug("Token Initialized: " + userToken);

		} catch (Exception e) {
			e.printStackTrace();
		}
    }

	public void destroy() { 
		Arc.container().requestContext().activate();
	}
}
