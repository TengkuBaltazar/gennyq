
package org.acme.travels.service;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;

import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.message.QDataAskMessage;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import life.genny.qwandaq.Answer;
import life.genny.qwandaq.Ask;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.BadDataException;
import life.genny.qwandaq.message.QDataAnswerMessage;
import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.qwandaq.utils.QuestionUtils;
import life.genny.qwandaq.utils.QwandaUtils;
import life.genny.serviceq.Service;

@ApplicationScoped
public class TaskService {

    private static final Logger log = Logger.getLogger(TaskService.class);

    Jsonb jsonb = JsonbBuilder.create();
    @Inject
    QuestionUtils questionUtils;

    @Inject
    DatabaseUtils databaseUtils;

    @Inject
    QwandaUtils qwandaUtils;

    @Inject
    EntityManager entityManager;

    @Inject
    Service service;

    public void enableTaskQuestion(Ask ask, Boolean enabled, String userTokenStr) {

        ask.setDisabled(!enabled);

        QDataAskMessage askMsg = new QDataAskMessage(ask);
        askMsg.setToken(userTokenStr);
        askMsg.setReplace(true);
        String sendingMsg = jsonb.toJson(askMsg);
        KafkaUtils.writeMsg("webcmds", sendingMsg);
    }

    public void hideTaskQuestion(Ask ask, Boolean hidden, String userTokenStr) {

        // Hide and Disable
        ask.setHidden(!hidden);
        ask.setDisabled(!hidden);

        QDataAskMessage askMsg = new QDataAskMessage(ask);
        askMsg.setToken(userTokenStr);
        askMsg.setReplace(true);
        String sendingMsg = jsonb.toJson(askMsg);
        KafkaUtils.writeMsg("webcmds", askMsg);
    }

}
