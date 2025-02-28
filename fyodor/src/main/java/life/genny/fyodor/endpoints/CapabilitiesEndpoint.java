package life.genny.fyodor.endpoints;

import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import life.genny.fyodor.utils.CapHandler;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.datatype.capability.core.Capability;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.intf.ICapabilityFilterable;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.DatabaseUtils;

@Path("/v1/caps")
public class CapabilitiesEndpoint {
    
 
    @Inject
    DatabaseUtils dbUtils;

    @Inject
    UserToken userToken;

    @PUT
    @Path("/ea/{product}/{baseEntityCode}/{attributeCode}")
    public Response updateEntityAttributeCapabilities(
        @PathParam("product") final String productCode, 
        @PathParam("baseEntityCode") final String baseEntityCode,
        @PathParam("attributeCode") final String attributeCode, 
        final Capability[] capabilityRequirements) 
    {
        if(!CapHandler.hasSecureToken(userToken))
            return Response.status(Status.FORBIDDEN).entity("Permission denied. User not Service user!").build();

        BaseEntity baseEntity = dbUtils.findBaseEntityByCode(productCode, baseEntityCode);
        if(baseEntity == null) {
            return Response
                .status(Status.NOT_FOUND)
                .entity("Could not find BaseEntity: " + baseEntityCode + " in product: " + productCode)
                .build();
        }

        Optional<EntityAttribute> entityAttributeOpt = baseEntity.findEntityAttribute(attributeCode);
        if(!entityAttributeOpt.isPresent()) {
            return Response
                .status(Status.NOT_FOUND)
                .entity("Could not find EntityAttribute: " + attributeCode + " in BaseEntity: " + baseEntityCode + " in product: " + productCode)
                .build();
        }

        ICapabilityFilterable filterable = entityAttributeOpt.get();
        dbUtils.updateCapabilityRequirements(productCode, filterable, capabilityRequirements);

        return Response.status(Status.OK).build();
    }

    @PUT
    @Path("/be/{product}/{code}")
    public Response updateBaseEntityCapabilities(
        @PathParam("product") final String productCode, 
        @PathParam("code") final String baseEntityCode, 
        final Capability[] capabilityRequirements) 
    {
        if(!CapHandler.hasSecureToken(userToken))
            return Response.status(Status.FORBIDDEN).entity("Permission denied. User not Service user!").build();

        ICapabilityFilterable filterableQuestion = dbUtils.findQuestionByCode(productCode, baseEntityCode);
        if(filterableQuestion == null) {
            return Response
                .status(Status.NOT_FOUND)
                .entity("Could not find BaseEntity: " + baseEntityCode + " in product: " + productCode)
                .build();
        }
        
        dbUtils.updateCapabilityRequirements(productCode, filterableQuestion, capabilityRequirements);

        return Response.status(Status.OK).build();
    }
    
    @PUT
    @Path("/q/{product}/{code}")
    public Response updateQuestionCapabilities(
        @PathParam("product") final String productCode, 
        @PathParam("code") final String questionCode, 
        final Capability[] capabilityRequirements) 
    {
        if(!CapHandler.hasSecureToken(userToken))
            return Response.status(Status.FORBIDDEN).entity("Permission denied. User not Service user!").build();

        ICapabilityFilterable filterableQuestion = dbUtils.findQuestionByCode(productCode, questionCode);
        if(filterableQuestion == null) {
            return Response
                .status(Status.NOT_FOUND)
                .entity("Could not find question: " + questionCode + " in product: " + productCode)
                .build();
        }
        
        dbUtils.updateCapabilityRequirements(productCode, filterableQuestion, capabilityRequirements);

        return Response.status(Status.OK).build();
    }

    @PUT
    @Path("/qq/{product}/{sourceCode}/{targetCode}")
    public Response updateQuestionQuestionCapabilities(
        @PathParam("product") final String productCode, 
        @PathParam("sourceCode") final String sourceCode,
        @PathParam("targetCode") final String targetCode, 
        final Capability[] capabilityRequirements) 
    {
        if(!CapHandler.hasSecureToken(userToken))
            return Response.status(Status.FORBIDDEN).entity("Permission denied. User not Service user!").build();

        ICapabilityFilterable filterableQuestion = dbUtils.findQuestionQuestionBySourceAndTarget(productCode, sourceCode, targetCode);
        if(filterableQuestion == null) {
            return Response
                .status(Status.NOT_FOUND)
                .entity("Could not find QuestionQuestion: [" + sourceCode + "/" + targetCode + "] in product: " + productCode)
                .build();
        }
        
        dbUtils.updateCapabilityRequirements(productCode, filterableQuestion, capabilityRequirements);

        return Response.status(Status.OK).build();
    }
}
