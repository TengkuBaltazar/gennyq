package life.genny.gadaq.service;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.SearchEntity;
import life.genny.qwandaq.message.QDataAttributeMessage;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.serviceq.Service;

/**
 * A Service class used for Auth Init operations.
 *
 * @author Jasper Robison
 */
@ApplicationScoped
public class InitService {

    private static final Logger log = Logger.getLogger(InitService.class);

	Jsonb jsonb = JsonbBuilder.create();

    @Inject
    Service service;

    @Inject
    DatabaseUtils databaseUtils;

    @Inject
    BaseEntityUtils beUtils;

	@Inject
	UserToken userToken;

	/**
	* Send the Project BaseEntity.
	 */
	public void sendProject() {

		log.info("Sending Project PRJ_" + userToken.getProductCode().toUpperCase());

		// grab baseentity for the project
		BaseEntity projectBE = beUtils.getProjectBaseEntity();

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(projectBE);
		msg.setToken(userToken.getToken());
		msg.setAliasCode("PROJECT");

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send the User.
	 */
	public void sendUser() {

		log.info("Sending User " + userToken.getUserCode());

		// fetch the users baseentity
		BaseEntity userBE = beUtils.getBaseEntityByCode(userToken.getUserCode());

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(userBE);
		msg.setToken(userToken.getToken());
		msg.setAliasCode("USER");

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send All attributes for the productCode.
	 */
	public void sendAllAttributes() {

		log.info("Sending Attributes for " + userToken.getProductCode());
		String productCode = userToken.getProductCode();

		// fetch bulk attribute msg from cache
		QDataAttributeMessage msg = CacheUtils.getObject(productCode, "ALL_ATTRIBUTES", QDataAttributeMessage.class);
		
		if (msg == null) {
			log.error("No attribute msg cached for " + productCode);
			return;
		}

		// set token and send
		msg.setToken(userToken.getToken());
		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send PCM BaseEntities.
	 */
	public void sendPCMs() {

		log.info("Sending PCMs for " + userToken.getProductCode());
		String productCode = userToken.getProductCode();

		// get pcms using search
		SearchEntity searchBE = new SearchEntity("SBE_PCMS", "PCM Search")
			.addSort("PRI_CREATED", "Created", SearchEntity.Sort.ASC)
			.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "PCM_%")
			.addColumn("*", "All Columns");

		searchBE.setRealm(productCode);
		List<BaseEntity> pcms = beUtils.getBaseEntitys(searchBE);

		// configure msg and send
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(pcms);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);

		KafkaUtils.writeMsg("webdata", msg);
	}

}
