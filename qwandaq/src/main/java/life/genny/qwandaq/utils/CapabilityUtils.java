package life.genny.qwandaq.utils;

import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.attribute.AttributeText;
import life.genny.qwandaq.attribute.EntityAttribute;
import life.genny.qwandaq.constants.GennyConstants;

import life.genny.qwandaq.datatype.CapabilityMode;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.checked.RoleException;
import life.genny.qwandaq.exception.runtime.NullParameterException;
import life.genny.qwandaq.models.ServiceToken;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.serialization.baseentity.BaseEntityKey;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * A non-static utility class for managing roles and capabilities.
 * 
 * @author Adam Crow
 * @author Jasper Robison
 * @author Bryn Meachem
 */
@ApplicationScoped
public class CapabilityUtils {
	protected static final Logger log = Logger.getLogger(CapabilityUtils.class);
	
	static Jsonb jsonb = JsonbBuilder.create();

	// Capability Attribute Prefix
	public static final String CAP_CODE_PREFIX = "PRM_";
	public static final String ROLE_BE_PREFIX = "ROL_";

	public static final String PRI_IS_PREFIX = "PRI_IS_";

	// TODO: Confirm we want DEFs to have capabilities as well
	public static final String[] ACCEPTED_CAP_PREFIXES = { ROLE_BE_PREFIX, "PER_", "DEF_" };

	public static final String LNK_ROLE_CODE = "LNK_ROLE";
	public static final String LNK_DEF_CODE = "LNK_DEF";

	List<Attribute> capabilityManifest = new ArrayList<Attribute>();

	@Inject
	UserToken userToken;

	@Inject
	ServiceToken serviceToken;

	@Inject
	DatabaseUtils dbUtils;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	BaseEntityUtils beUtils;

	public CapabilityUtils() {
	}

	/**
	 * Add a capability to a BaseEntity.
	 * @param productCode The product code
	 * @param target The target entity
	 * @param capabilityCode The capability code
	 * @param modes The modes to set
	 */
	public void updateCapabilityCache(String productCode, BaseEntity target, final String capabilityCode,
			final CapabilityMode... modes) {

		String code = cleanCapabilityCode(capabilityCode);
		String key = String.format("%s:%s", target.getCode(), code);

		Set<CapabilityMode> set = new HashSet<>(Arrays.asList(modes));

		CacheUtils.putObject(productCode, key, set);
		log.infof("[^] Cached in %s -> %s:%s", productCode, target.getCode(), code);
	}


	public BaseEntity inheritRole(BaseEntity role, final BaseEntity parentRole) {
		BaseEntity ret = role;
		List<EntityAttribute> perms = parentRole.findPrefixEntityAttributes(CAP_CODE_PREFIX);
		for (EntityAttribute permissionEA : perms) {
			Attribute permission = permissionEA.getAttribute();
			CapabilityMode[] modes = getCapModesFromString(permissionEA.getValue());
			ret = addCapabilityToBaseEntity(ret, permission.getCode(), modes);
		}
		return ret;
	}

	public Attribute addCapability(final String rawCapabilityCode, final String name) {
		String cleanCapabilityCode = cleanCapabilityCode(rawCapabilityCode);
		log.info("Setting Capability : " + cleanCapabilityCode + " : " + name);
		
		Attribute attribute = qwandaUtils.getAttribute(cleanCapabilityCode);

		if (attribute == null) {
			attribute = new AttributeText(cleanCapabilityCode, name);
			qwandaUtils.saveAttribute(attribute);
		}

		capabilityManifest.add(attribute);
		return attribute;
	}

	public BaseEntity addCapabilityToBaseEntity(BaseEntity targetBe, final String rawCapabilityCode,
			final CapabilityMode... modes) {
		// Ensure the capability is well defined
		String cleanCapabilityCode = cleanCapabilityCode(rawCapabilityCode);
		// Check the user token has required capabilities
		if (!hasCapability(cleanCapabilityCode, modes)) {
			log.error(userToken.getUserCode() + " is NOT ALLOWED TO ADD CAP: " + cleanCapabilityCode
					+ " TO BASE ENTITITY: " + targetBe.getCode());
			return targetBe;
		}

		updateCachedRoleSet(targetBe.getCode(), cleanCapabilityCode, modes);
		return targetBe;
	}

	/**
	 * Get a set of capability modes for a target and capability combination.
	 * @param target The target entity
	 * @param capabilityCode The capability code
	 * @return An array of CapabilityModes
	 */
	private Set<CapabilityMode> getCapabilitiesFromCache(final String targetCode, 
			final String capabilityCode) throws RoleException {

		String productCode = userToken.getProductCode();
		String key = String.format("%s:%s", targetCode, capabilityCode);

		Set<CapabilityMode> modes = CacheUtils.getObject(productCode, key, HashSet.class);
		if (modes == null)
			throw new RoleException("Nothing present for capability combination: " + key);

		return modes;
	}

	/**
	 * @param role
	 * @param capabilityCode
	 * @param mode
	 */
	private String updateCachedRoleSet(final String roleCode, final String cleanCapabilityCode,
			final CapabilityMode... modes) {
		String productCode = userToken.getProductCode();
		String key = getCacheKey(roleCode, cleanCapabilityCode);
		String modesString = getModeString(modes);

		log.info("updateCachedRoleSet test:: " + key);
		// if no cache then create
		return CacheUtils.writeCache(productCode, key, modesString);
	}

	/**
	 * Go through a list of capability modes and check that the token can manipulate
	 * the modes for the provided capabilityCode
	 * 
	 * @param rawCapabilityCode capabilityCode to check against (will be cleaned before use)
	 * @param checkModes          array of modes to check against
	 * @return whether or not the token can manipulate all the supplied modes for
	 *         the supplied capabilityCode
	 */
	public boolean hasCapability(final String rawCapabilityCode, final CapabilityMode... checkModes) {
		// allow keycloak admin and devcs to do anything
		if (userToken.hasRole("admin", "dev")) {
			return true;
		}
		final String cleanCapabilityCode = cleanCapabilityCode(rawCapabilityCode);
		BaseEntity user = beUtils.getUserBaseEntity();
		List<String> codes = beUtils.getBaseEntityCodeArrayFromLinkAttribute(user, LNK_ROLE_CODE);

		// Make a list for the modes that have been found in the user's various roles
		// TODO: Potentially change this to a system that matches from multiple roles
		// instead of a single role
		// List<CapabilityMode> foundModes = new ArrayList<>();

		for (String code : codes) {
			try {
				Set<CapabilityMode> modes = getCapabilitiesFromCache(code, cleanCapabilityCode);
				for (CapabilityMode checkMode : checkModes) {
					if (!modes.contains(checkMode))
						return false;
				}
			} catch (RoleException e) {
				log.debug(e.getMessage());
				return false;
			}
		}

		// TODO: Implement user checking
		Set<EntityAttribute> entityAttributes = user.getBaseEntityAttributes();
		for(EntityAttribute eAttribute : entityAttributes) {
			if(!eAttribute.getAttributeCode().startsWith(CAP_CODE_PREFIX))
				continue;
		}

		// Since we are iterating through an array of modes to check, the above impl
		// will have returned false if any of them were missing
		return true;
	}

	private boolean shouldOverride() {
		// allow keycloak admin and devcs to do anything
		return (userToken.hasRole("admin", "dev") || ("service".equals(userToken.getUsername())));
	}

	public boolean hasCapabilityThroughRoles(String rawCapabilityCode, CapabilityMode mode) {
		if(shouldOverride())
			return true;

		final String cleanCapabilityCode = cleanCapabilityCode(rawCapabilityCode);
		BaseEntity user = beUtils.getUserBaseEntity();
		if(user == null) {
			log.error("Null user detected for token with uuid: " + userToken.getUuid());
			log.error("Token: " + userToken.getToken());
			return false;
		}
		JsonArray roles = jsonb.fromJson(user.getValueAsString(LNK_ROLE_CODE), JsonArray.class);

		BaseEntity currentRole;
		for(int i = 0; i < roles.size(); i++) {
			String roleCode = roles.getString(i);
			currentRole = beUtils.getBaseEntityByCode(roleCode);
			if(currentRole == null) {
				log.error("Could not find role when looking at PRI IS for base entity " + user.getCode() + ": " + roleCode);
				continue;
			}

			Optional<EntityAttribute> optEa = currentRole.findEntityAttribute(cleanCapabilityCode);
			if (optEa.isEmpty())
				continue;
			EntityAttribute ea = optEa.get();
			String modes = ea.getValueString();
			if(StringUtils.isBlank(modes)) {
				log.error("Dumb Capability detected! Role: " + roleCode + " has capability " + cleanCapabilityCode + " but not modes attached!");
				continue;
			}

			if(modes.contains(mode.name()))
				return true;
		}

		return false;
	}

	/**
	 * Checks if the user has a capability using any PRI_IS_ attributes.
	 *
	 * NOTE: This should be temporary until the LNK_ROLE attribute is properly in place!
	 * Lets do it in 10.1.0!!!
	 *
	 * @param rawCapabilityCode The code of the capability.
	 * @param mode The mode of the capability.
	 * @return Boolean True if the user has the capability, False otherwise.
	 */
	public boolean hasCapabilityThroughPriIs(String rawCapabilityCode, CapabilityMode mode) {
		log.info("Assessing roles through PRI_IS attribs for user with uuid: " + userToken.getCode());
		if(shouldOverride())
			return true;

		final String cleanCapabilityCode = cleanCapabilityCode(rawCapabilityCode);
		BaseEntity user = beUtils.getUserBaseEntity();
		if(user == null) {
			log.error("Null user detected for token: " + userToken.getToken());
			return false;
		}
		List<EntityAttribute> priIsAttributes = user.findPrefixEntityAttributes(PRI_IS_PREFIX);

		if (priIsAttributes.isEmpty()) {
			log.error("Could not find any PRI_IS attributes for base entity: " + user.getCode());
			return false;
		}

		return priIsAttributes.stream().anyMatch((EntityAttribute priIsAttribute) -> {
			String priIsCode = priIsAttribute.getAttributeCode();
			String roleCode = ROLE_BE_PREFIX + priIsCode.substring(PRI_IS_PREFIX.length());
			log.debug("[!] Scanning Role: " + roleCode);
			BaseEntity roleBe = beUtils.getBaseEntityByCode(roleCode);
			if(roleBe == null) {
				return false;
			}

			String modeString = roleBe.getValueAsString(cleanCapabilityCode);
			if(StringUtils.isBlank(modeString))
				return false;
			return modeString.contains(mode.name());
		});
	}

	// TODO: Rewrite
	public void process() {
		String productCode = userToken.getProductCode();

		// Find all existing capabilities for a given product code
		// remove all capabilities in the manifest

		// Process the rest of the existing capabilities??
			// remove existing capability 
			// remove said capability from all roles that contain it
			// update roles in database and cache
		List<Attribute> existingCapability = dbUtils.findAttributesWithPrefix(productCode, CAP_CODE_PREFIX);

		/* Remove any capabilities not in this forced list from roles */
		existingCapability.removeAll(getCapabilityManifest());

		/*
		 * for every capability that exists that is not in the manifest , find all
		 * entityAttributes
		 */
		for (Attribute toBeRemovedCapability : existingCapability) {
			// Remove capability from db
			String attributeCode = toBeRemovedCapability.getCode();
			dbUtils.deleteAttribute(productCode, attributeCode);
			/* update all the roles that use this attribute by reloading them into cache */
			List<BaseEntity> cachedRoles = CacheUtils.getBaseEntitiesByPrefix(productCode, ROLE_BE_PREFIX);
			if (!cachedRoles.isEmpty()) {
				for (BaseEntity role : cachedRoles) {
					String roleCode = role.getCode();
					CacheUtils.removeEntry(productCode, getCacheKey(roleCode, attributeCode));
					role.removeAttribute(toBeRemovedCapability.getCode());
					/* Now update the db role to only have the attributes we want left */
					BaseEntityKey beKey = new BaseEntityKey(productCode, role.getCode());
					CacheUtils.saveEntity(productCode, beKey, role);
				}
			}
		}
	}

	/**
	 * @return the beUtils
	 */
	public BaseEntityUtils getBeUtils() {
		return beUtils;
	}

	/**
	 * @return the capabilityManifest
	 */
	public List<Attribute> getCapabilityManifest() {
		return capabilityManifest;
	}

	/**
	 * @param capabilityManifest the capabilityManifest to set
	 */
	public void setCapabilityManifest(List<Attribute> capabilityManifest) {
		this.capabilityManifest = capabilityManifest;
	}
/**
	 * @param condition the condition to check
	 * @return Boolean
	 */
	public Boolean conditionMet(String condition) {

		if (condition == null) {
			log.error("condition is NULL!");
			return false;
		}

		log.info("Testing condition with value: " + condition);
		String[] conditionArray = condition.split(":");

		String capability = conditionArray[0];
		String mode = conditionArray[1];

		// check for NOT operator
		Boolean not = capability.startsWith("!");
		capability = not ? capability.substring(1) : capability;

		// check for Capability
		Boolean hasCap = hasCapabilityThroughPriIs(capability, CapabilityMode.getMode(mode));

		// XNOR operator
		return hasCap ^ not;
	}
	
	@Override
	public String toString() {
		return "CapabilityUtils [" + (capabilityManifest != null ? "capabilityManifest=" + capabilityManifest : "")
				+ "]";
	}

	public static String getModeString(CapabilityMode... modes) {
		return CommonUtils.getArrayString(modes, Enum::name);
	}

	public static CapabilityMode[] getCapModesFromString(String modeString) {
		JsonArray array = jsonb.fromJson(modeString, JsonArray.class);
		CapabilityMode[] modes = new CapabilityMode[array.size()];

		for (int i = 0; i < array.size(); i++) {
			modes[i] = CapabilityMode.valueOf(array.getString(i));
		}

		return modes;
	}

	public static String cleanCapabilityCode(final String rawCapabilityCode) {
		String cleanCapabilityCode = rawCapabilityCode.toUpperCase();
		if (!cleanCapabilityCode.startsWith(CAP_CODE_PREFIX)) {
			cleanCapabilityCode = CAP_CODE_PREFIX + cleanCapabilityCode;
		}

		String[] components = cleanCapabilityCode.split("_");
		// Should be of the form PRM_<OWN/OTHER>_<CODE>
		/*
		 * 1. PRM
		 * 2. OWN or OTHER
		 * 3. CODE
		 */
		if (components.length < 3) {
			log.warn("Capability Code: " + rawCapabilityCode + " missing OWN/OTHER declaration.");
		} else {
			boolean affectsOwn = "OWN".equals(components[1]);
			boolean affectsOther = "OTHER".equals(components[1]);

			if (!affectsOwn && !affectsOther) {
				log.warn("Capability Code: " + rawCapabilityCode + " has malformed OWN/OTHER declaration.");
			}
		}

		return cleanCapabilityCode;
	}

	/**
	 * Construct a cache key for fetching capabilities
	 * @param roleCode
	 * @param capCode
	 * @return
	 */
	private static String getCacheKey(String roleCode, String capCode) {
		return roleCode + ":" + capCode;
	}

	/**
	 * Get a redirect code for user based on their roles.
	 * @return The redirect code
	 * @throws RoleException If no roles are found for the user, or 
	 * 		none of roles found have any associated redirect code
	 */
	public String getUserRoleRedirectCode() throws RoleException {
		
		// grab user role codes
		BaseEntity user = beUtils.getUserBaseEntity();
		List<String> roles = beUtils.getBaseEntityCodeArrayFromLinkAttribute(user, "LNK_ROLE");

		if (roles == null || roles.isEmpty())
			throw new RoleException(String.format("No roles found for user %s", user.getCode()));

		log.debug("User " + user.getCode() + " roles: " + roles.toString());

		// TODO: return redirect for roles based on priority
		for (String role : roles) {
			try {
				// return first found redirect
				return getRoleRedirectCode(role);
			} catch (RoleException e) {
				log.debug(e.getMessage());
			}
		}

		throw new RoleException(String.format("No redirect in roles %s", roles));
	}


	/**
	 * Set the redirect code for a role.
	 * @param productCode The product code
	 * @param role The role to set the redirect for
	 * @param redirectCode The code to set as redirect
	 */
	public void setRoleRedirect(String productCode, BaseEntity role, String redirectCode) {
		 
		CacheUtils.putObject(productCode, String.format("%s:REDIRECT", role.getCode()), redirectCode);
	}

	/**
	 * Get the redirect code for a role.
	 * @param roleCode The code of the role
	 * @return The redirect code
	 * @throws RoleException If no redirect is found for the role
	 */
	public String getRoleRedirectCode(String roleCode) throws RoleException {
		
		if (roleCode == null)
			throw new NullParameterException(roleCode);

		String product = userToken.getProductCode();
		String key = String.format("%s:REDIRECT", roleCode);

		log.debug(key);

		// TODO: grab redirect for role
		String redirectCode = CacheUtils.getObject(product, key, String.class);

		if (redirectCode == null)
			throw new RoleException(String.format("No redirect found in role %s", roleCode));

		return redirectCode;
	}
}
