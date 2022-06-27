package life.genny.gadaq.live.data;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.annotations.Blocking;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import life.genny.kogito.common.utils.KogitoUtils;
import life.genny.qwandaq.Answer;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.constants.GennyConstants;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.message.QDataAnswerMessage;
import life.genny.qwandaq.message.QEventMessage;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.serialization.baseentity.BaseEntityKey;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.QwandaUtils;
import life.genny.serviceq.Service;
import life.genny.serviceq.intf.GennyScopeInit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.kie.api.runtime.KieRuntimeBuilder;
import org.kie.api.runtime.KieSession;

@ApplicationScoped
public class InternalConsumer {

	static final Logger log = Logger.getLogger(InternalConsumer.class);

	static Jsonb jsonb = JsonbBuilder.create();

	@ConfigProperty(name = "kogito.service.url", defaultValue = "http://alyson.genny.life:8250")
	String myUrl;

	@Inject
	Service service;

	@Inject
	GennyScopeInit scope;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	UserToken userToken;

	@Inject
	BaseEntityUtils beUtils;

	@Inject
	KogitoUtils kogitoUtils;

	@Inject
	DatabaseUtils databaseUtils;

	@Inject
	KieRuntimeBuilder kieRuntimeBuilder;

	KieSession ksession;

	/**
	 * Execute on start up.
	 *
	 * @param ev The startup event
	 */
	void onStart(@Observes StartupEvent ev) {
		service.fullServiceInit();
	}

	/**
	 * Fetch target baseentity from cache 'baseentity'
	 * Add/Replace EntityAttribute value from answer
	 * Push back the baseentity into 'baseentity' cache
	 */
	// TODO: Potentially removing this
	@Blocking
	@Incoming("answer")
	public void fromAnswers(String payload) {

		Instant start = Instant.now();
		scope.init(payload);

		Answer answer = jsonb.fromJson(payload, Answer.class);

		String targetCode = answer.getTargetCode();
		String productCode = userToken.getProductCode();
		String attributeCode = answer.getAttributeCode();
		String ansValue = answer.getValue();

		log.info("Processing answer:" + targetCode + ":" + attributeCode + "=" + ansValue);

		if (ansValue != null && ansValue.length() <= 50) {
			log.debug("[!] Received Kafka Answer!");
			log.debug("Target: " + targetCode);
			log.debug("Attribute Code: " + attributeCode);
			log.debug("Value: " + ansValue);
			log.debug("Token Product Code: " + productCode);
			log.debug("================= END ANSWER ==================");
		}

		if ("backend".equals(productCode)) {
			productCode = "internmatch";
		}

		// Retrieve Base Entity from cache

		BaseEntityKey baseEntityKey = new BaseEntityKey(productCode, targetCode);
		log.info("Fetching BaseEntity from '" + GennyConstants.CACHE_NAME_BASEENTITY + "': " + targetCode);
		log.info("	- Key: " + baseEntityKey);

		// BaseEntity targetBaseEntity = (BaseEntity)
		// CacheUtils.getEntity(GennyConstants.CACHE_NAME_BASEENTITY,
		// baseEntityKey);

		BaseEntity targetBaseEntity = (BaseEntity) databaseUtils.findBaseEntityByCode(productCode, targetCode);

		if (targetBaseEntity == null) {
			log.error("Error retrieving base entity: [" + targetCode + "] for product code: " + productCode);

			scope.destroy();
			return;
		}

		// Update the EntityAttribute
		Optional<EntityAttribute> optEA = targetBaseEntity.findEntityAttribute(attributeCode);

		if (optEA.isPresent()) {
			EntityAttribute entityAttribute = optEA.get();
			entityAttribute.setValue(ansValue);
		} else {
			log.error("Could not find attribute " + attributeCode + " in BaseEntity: " + targetBaseEntity.getCode());

			scope.destroy();
			return;
		}

		// Send the baseentity back into the cache
		BaseEntity cachedBaseEntity = (BaseEntity) CacheUtils.saveEntity(GennyConstants.CACHE_NAME_BASEENTITY,
				baseEntityKey, targetBaseEntity);

		if (cachedBaseEntity == null) {
			log.error("Error Saving BaseEntity: " + targetBaseEntity.getCode());
			log.error("Cache: " + GennyConstants.CACHE_NAME_BASEENTITY);
			log.error("BaseEntityKey: " + baseEntityKey);

			scope.destroy();
			return;
		}
		try {
			qwandaUtils.saveAnswer(answer);
		} catch (NullPointerException npe) {
			log.error("Error saving answer: " + answer.getTargetCode() + ":" + answer.getAttributeCode() + "="
					+ answer.getValue());
		}
		scope.destroy();
		Instant end = Instant.now();
		log.info("Duration = " + Duration.between(start, end).toMillis() + "ms");
	}

	@Incoming("genny_data")
	@Blocking
	public void getData(String data) {
		scope.init(data);

		log.info("Received Forwarded Data : " + data);
		log.info("userToken = " + userToken);
		log.info("productCode = " + userToken.getProductCode());

		Instant start = Instant.now();

		// check if event is a valid event
		log.info("Parsing msg");
		QDataAnswerMessage msg = null;
		try {
			msg = jsonb.fromJson(data, QDataAnswerMessage.class);
		} catch (Exception e) {
			log.error("Cannot parse this data!");
			e.printStackTrace();
			return;
		}

		log.info("Getting session");

		// start new session
		KieSession session = kieRuntimeBuilder.newKieSession();

		log.info("Inserting facts");
		session.insert(kogitoUtils);
		session.insert(beUtils);
		session.insert(userToken);
		session.insert(msg);

		// Infer data
		log.info("Firing Rules");
		try {
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		// check for event based answers
		for (Answer answer : msg.getItems()) {

			// skip if no processId is present
			if ("no-id".equals(answer.getProcessId())) {
				continue;
			}

			String processId = answer.getProcessId();
			String answerJson = jsonb.toJson(answer);

			kogitoUtils.sendSignal("processQuestions", processId, "answer", answerJson);
		}

		Instant end = Instant.now();
		log.info("Duration = " + Duration.between(start, end).toMillis() + "ms");
		scope.destroy();
	}

	/**
	 * Consume from the genny_events topic.
	 *
	 * @param event The incoming event
	 */
	@Incoming("genny_events")
	@Blocking
	public void getEvent(String event) {

		scope.init(event);

		log.info("Received Forwarded Event : " + event);
		log.info("userToken = " + userToken);
		log.info("productCode = " + userToken.getProductCode());
		Instant start = Instant.now();

		// check if event is a valid event
		log.info("Parsing msg");
		QEventMessage msg = null;
		try {
			msg = jsonb.fromJson(event, QEventMessage.class);
		} catch (Exception e) {
			log.error("Cannot parse this event!");
			e.printStackTrace();
			return;
		}
		log.info("Getting session");

		// start new session
		KieSession session = kieRuntimeBuilder.newKieSession();

		log.info("Inserting facts");
		session.insert(kogitoUtils);
		session.insert(beUtils);
		session.insert(userToken);
		session.insert(msg);

		log.info("Firing Rules");
		try {
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		Instant end = Instant.now();
		log.info("Duration = " + Duration.between(start, end).toMillis() + "ms");

		scope.destroy();
	}
}
