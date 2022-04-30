package life.genny.gadaq.service;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.entity.SearchEntity;
import life.genny.qwandaq.message.QDataAttributeMessage;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.models.TokenCollection;
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

    @Inject
    Service service;

    @Inject
    DatabaseUtils databaseUtils;

    @Inject
    BaseEntityUtils beUtils;

	@Inject
	TokenCollection tokens;

	/**
	* Send the Project BaseEntity.
	 */
	public void sendProject() {

		log.info("Sending Project");

		BaseEntity projectBE = beUtils.getProjectBaseEntity();

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(projectBE);
		msg.setToken(tokens.getGennyToken().getToken());
		msg.setAliasCode("PROJECT");

		log.info("Sending Project down kafka");

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send the User.
	 */
	public void sendUser() {

		log.info("Sending User");

		BaseEntity userBE = beUtils.getBaseEntityByCode(tokens.getGennyToken().getCode());

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(userBE);
		msg.setToken(tokens.getGennyToken().getToken());
		msg.setAliasCode("USER");

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send All attributes for the productCode.
	 */
	public void sendAllAttributes() {

		log.info("Sending Attributes");

		GennyToken gennyToken = tokens.getGennyToken();
		String productCode = gennyToken.getProductCode();

		QDataAttributeMessage msg = CacheUtils.getObject(productCode, "ALL_ATTRIBUTES", QDataAttributeMessage.class);
		msg.setToken(gennyToken.getToken());

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	* Send PCM BaseEntities.
	 */
	public void sendPCMs() {

		log.info("Sending PCMs");

		GennyToken gennyToken = tokens.getGennyToken();
		String productCode = gennyToken.getProductCode();

		// get pcms using search
		SearchEntity searchBE = new SearchEntity("SBE_PCMS", "PCM Search")
			.addSort("PRI_CREATED", "Created", SearchEntity.Sort.ASC)
			.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "PCM_%")
			.addColumn("*", "All Columns");

		searchBE.setRealm(productCode);
		List<BaseEntity> pcms = beUtils.getBaseEntitys(searchBE);

		// send to frontend
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(pcms);
		msg.setToken(gennyToken.getToken());
		msg.setReplace(true);

		KafkaUtils.writeMsg("webdata", msg);
	}

}
