package life.genny.kogito.common.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import life.genny.qwandaq.Ask;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.BadDataException;
import life.genny.qwandaq.message.QCmdMessage;
import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.qwandaq.utils.QwandaUtils;

@ApplicationScoped
public class FrontendService {

	private static final Logger log = Logger.getLogger(FrontendService.class);

	Jsonb jsonb = JsonbBuilder.create();

	@Inject
	UserToken userToken;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	DatabaseUtils databaseUtils;

	@Inject
	BaseEntityUtils beUtils;

	/**
	 * Get asks using a question code, for a given source and target.
	 *
	 * @param questionCode The question code used to fetch asks
	 * @param sourceCode The code of the source entity
	 * @param targetCode The code of the target entity
	 * @param processId The processId to set in the asks
	 * @return The ask message
	 */
	public String getAsks(String questionCode, String sourceCode, String targetCode, String processId) {

		log.info("questionCode :" + questionCode);
		log.info("sourceCode :" + sourceCode);
		log.info("targetCode :" + targetCode);
		log.info("processId :" + processId);

		BaseEntity source = beUtils.getBaseEntityByCode(sourceCode);
		BaseEntity target = beUtils.getBaseEntityByCode(targetCode);

		if (source == null) {
			log.error("No Source entity found!");
			return null;
		}

		if (target == null) {
			log.error("No Target entity found!");
			return null;
		}

		log.info("Fetching asks -> " + questionCode + ":" + source.getCode() + ":" + target.getCode());

		// fetch question from DB
		Ask ask = qwandaUtils.generateAskFromQuestionCode(questionCode, source, target);

		if (ask == null) {
			log.error("No ask returned for " + questionCode);
			return null;
		}

		qwandaUtils.recursivelySetProcessId(ask, processId);

		// create ask msg from asks
		log.info("Creating ask Message...");
		QDataAskMessage msg = new QDataAskMessage(ask);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);

		return jsonb.toJson(msg);
	}

	/**
	 * Setup the process entity used to store task data.
	 *
	 * @param targetCode The code of the target entity
	 * @param askMessageJson The ask message to use in setup
	 * @return The updated process entity
	 */
	public String setupProcessBE(String targetCode, String askMessageJson) {

		if (askMessageJson == null) {
			log.error("Ask message Json must not be null!");
			return null;
		}

		QDataAskMessage askMsg = jsonb.fromJson(askMessageJson, QDataAskMessage.class);

		// init entity and force the realm
		log.info("Creating Process Entity...");
		BaseEntity processBE = new BaseEntity("QBE_"+targetCode.substring(4), "QuestionBE");
		processBE.setRealm(userToken.getProductCode());

		// only copy the entityAttributes used in the Asks
		BaseEntity target = beUtils.getBaseEntityByCode(targetCode);

		// find all allowed attribute codes
		Set<String> attributeCodes = new HashSet<>();
		for (Ask ask : askMsg.getItems()) {
			attributeCodes.addAll(qwandaUtils.recursivelyGetAttributeCodes(attributeCodes, ask));
		}

		log.info("Found " + attributeCodes.size() + " active attributes in asks");

		// add an entityAttribute to process entity for each attribute
		for (String code : attributeCodes) {

			// check for existing attribute in target
			EntityAttribute ea = target.findEntityAttribute(code).orElseGet(() -> {

				// otherwise create new attribute
				Attribute attribute = qwandaUtils.getAttribute(code);
				return new EntityAttribute(processBE, attribute, 1.0, null);
			});

			try {
				processBE.addAttribute(ea);
			} catch (BadDataException e) {
				e.printStackTrace();
			}
		}

		log.info("ProcessBE contains " + processBE.getBaseEntityAttributes().size() + " entity attributes");

		return jsonb.toJson(processBE);
	}

	/**
	 * Update the ask target to match the process entity code.
	 *
	 * @param processBEJson The json of the process entity
	 * @param askMessageJson The ask message to use in setup
	 * @return The updated ask message
	 */
	public String updateAskTarget(String processBEJson, String askMessageJson) {

		if (processBEJson == null) {
			log.error("Process Entity json must not be null!");
			return null;
		}

		if (askMessageJson == null) {
			log.error("Ask Message json must not be null!");
			return null;
		}

		BaseEntity processBE = jsonb.fromJson(processBEJson, BaseEntity.class);
		QDataAskMessage askMsg = jsonb.fromJson(askMessageJson, QDataAskMessage.class);

		recursivelyUpdateAskTarget(askMsg.getItems().get(0), processBE);

		return jsonb.toJson(askMsg);
	}

	/**
	 * Recursively update the ask target.
	 *
	 * @param ask The ask to traverse
	 * @param target The target entity to set
	 */
	public void recursivelyUpdateAskTarget(Ask ask, BaseEntity target) {

		ask.setTargetCode(target.getCode());

		// recursively update children
		if (ask.getChildAsks() != null) {
			for (Ask child : ask.getChildAsks()) {
				recursivelyUpdateAskTarget(child, target);
			}
		}
	}

	/**
	 * Send a baseentity after filtering the entity attributes 
	 * based on the questions in the ask message.
	 *
	 * @param code The code of the baseentity to send
	 * @param askMsg The ask message used to filter attributes
	 */
	public void sendBaseEntitys(String processBEJson, String askMessageJson) {

		BaseEntity processBE = jsonb.fromJson(processBEJson, BaseEntity.class);
		QDataAskMessage askMsg = jsonb.fromJson(askMessageJson, QDataAskMessage.class);

		// find all allowed attribute codes
		Set<String> attributeCodes = new HashSet<>();
		for (Ask ask : askMsg.getItems()) {
			attributeCodes.addAll(qwandaUtils.recursivelyGetAttributeCodes(attributeCodes, ask));
		}

		// grab all entityAttributes from the entity
		Set<EntityAttribute> entityAttributes = ConcurrentHashMap.newKeySet(processBE.getBaseEntityAttributes().size());
		for (EntityAttribute ea : processBE.getBaseEntityAttributes()) {
			entityAttributes.add(ea);
		}

		// delete any attribute that is not in the allowed Set
		for (EntityAttribute ea : entityAttributes) {
			if (!attributeCodes.contains(ea.getAttributeCode())) {
				processBE.removeAttribute(ea.getAttributeCode());
			}
		}

		// send entity front end
		QDataBaseEntityMessage msg = new QDataBaseEntityMessage(processBE);
		msg.setToken(userToken.getToken());
		msg.setReplace(true);
		KafkaUtils.writeMsg("webdata", msg);

		List<BaseEntity> selections = new ArrayList<>();

		// find non-empty selections
		processBE.findPrefixEntityAttributes("LNK_").stream()
			.filter(ea -> !StringUtils.isEmpty(ea.getValueString()))
			.forEach(ea -> {
				BaseEntity sel = beUtils.getBaseEntityFromLinkAttribute(processBE, ea.getAttributeCode());
				if (sel != null) {
					selections.add(sel);
				}
			});

		QDataBaseEntityMessage selectionMsg = new QDataBaseEntityMessage(selections);
		selectionMsg.setToken(userToken.getToken());
		selectionMsg.setReplace(true);
		KafkaUtils.writeMsg("webdata", selectionMsg);
	}

	/**
	 * Send an ask message to the frontend through the webdata topic.
	 *
	 * @param askMsgJson The ask message to send
	 */
	public void sendQDataAskMessage(String askMessageJson, String processBEJson) {

		BaseEntity processBE = jsonb.fromJson(processBEJson, BaseEntity.class);
		QDataAskMessage askMessage = jsonb.fromJson(askMessageJson, QDataAskMessage.class);

		// NOTE: We only ever check the first ask in the message
		Ask ask = askMessage.getItems().get(0);

		Boolean answered = qwandaUtils.mandatoryFieldsAreAnswered(ask, processBE);
		recursivelyFindAndUpdateSubmitDisabled(ask, !answered);

		KafkaUtils.writeMsg("webdata", askMessageJson);
	}

	/**
	 * Find the submit ask and update its disabled value.
	 *
	 * @param ask The ask to traverse
	 * @param disabled The value to set
	 * @return The submit ask
	 */
	public void recursivelyFindAndUpdateSubmitDisabled(Ask ask, Boolean disabled) {

		// return ask if submit is found
		if (ask.getAttributeCode().equals("PRI_SUBMIT")) {
			ask.setDisabled(disabled);
			return;
		}

		// ensure child asks is not null
		if (ask.getChildAsks() == null) {
			return;
		}

		// recursively check child asks for submit
		for (Ask child : ask.getChildAsks()) {
			recursivelyFindAndUpdateSubmitDisabled(child, disabled);
		}
	}

	/**
	 * Send dropdown items for the asks.
	 *
	 * @param askMsgJson The ask message to send
	 */
	public void sendDropdownItems(String askMessageJson) {

		QDataAskMessage askMessage = jsonb.fromJson(askMessageJson, QDataAskMessage.class);

		// NOTE: We only ever check the first ask in the message
		Ask ask = askMessage.getItems().get(0);

		BaseEntity target = beUtils.getBaseEntityByCode(ask.getTargetCode());

		QDataBaseEntityMessage msg = new QDataBaseEntityMessage();
		recursivelyHandleDropdownAttributes(ask, target, msg);

		KafkaUtils.writeMsg("webdata", msg);
	}

	/**
	 * Recursively traverse the asks and add any entity selections to the msg.
	 *
	 * @param ask The ask to traverse
	 * @param target The target entity used in finding values
	 * @param ask The msg to add entities to
	 */
	public void recursivelyHandleDropdownAttributes(Ask ask, BaseEntity target, QDataBaseEntityMessage msg) {

		// recursively handle any child asks
		if (ask.getChildAsks() != null) {
			for (Ask child : ask.getChildAsks()) {
				recursivelyHandleDropdownAttributes(child, target, msg);
			}
		}

		// check for dropdown attribute
		if (ask.getAttributeCode().startsWith("LNK_")) {

			// get list of value codes
			List<String> codes = beUtils.getBaseEntityCodeArrayFromLinkAttribute(target, ask.getAttributeCode());

			// fetch entity for each and add to msg
			for (String code : codes) {
				BaseEntity be = beUtils.getBaseEntityByCode(code);

				if (be != null) {
					msg.add(be);
				}
			}
		}
	}

	/**
	 * Send a command message based on a PCM code.
	 *
	 * @param code The code of the PCM baseentity
	 * @param questionCode The code of the question
	 */
	public void sendPCM(final String code, final String questionCode) {

		BaseEntity root = beUtils.getBaseEntityByCode("PCM_ROOT");

		try {
            root.setValue("PRI_LOC3", code);
        } catch (BadDataException e) {
            e.printStackTrace();
        }

		BaseEntity pcm = beUtils.getBaseEntityByCode(code);
		Attribute attribute = qwandaUtils.getAttribute("PRI_QUESTION_CODE");
		EntityAttribute ea = new EntityAttribute(pcm, attribute, 1.0, questionCode);

		try {
            pcm.addAttribute(ea);
        } catch (BadDataException e) {
            e.printStackTrace();
        }

        QDataBaseEntityMessage msg = new QDataBaseEntityMessage();
		msg.add(root);
		msg.add(pcm);

        msg.setToken(userToken.getToken());
        msg.setReplace(true);

        KafkaUtils.writeMsg("webdata", msg);
	}

	public void createBaseEntity(String defCode) {

		if (defCode == null || !defCode.startsWith("DEF_")) {
			log.error("Invalid defCode: " + defCode);
			return;
		}

		// fetch the def baseentity
		BaseEntity def = beUtils.getBaseEntityByCode(defCode);
		if(def == null) {
			log.error("Could not find DEF BaseEntity with code: " + defCode);
		}

		// use entity create function and save to db
		try {
			BaseEntity entity = beUtils.create(def);
			log.info("BaseEntity Created: " + entity.getCode());
		} catch (Exception e) {
			log.error("Error creating BaseEntity! DEF Code: " + defCode);
			e.printStackTrace();
		}
	}
}
