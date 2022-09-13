package life.genny.qwandaq.utils.capabilities;


import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;

import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.datatype.CapabilityMode;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.checked.RoleException;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;

public class RoleBuilder {
    static final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass());
    
    private CapabilityUtils cpUtils;

    private BaseEntity targetRole;
    
    private String productCode;

    private Map<String, Attribute> capabilityMap;

    private Map<String, CapabilityMode[]> roleCapabilities = new HashMap<>();

    // Attribs
    private String redirectCode;

    // TODO: Again I want to get rid of product code chains like this
    // TODO: Hopefully we can firm up how product codes are assigned to tokens
    public RoleBuilder(CapabilityUtils capabilityUtils, String roleCode, String roleName, String productCode) {
        this.cpUtils = capabilityUtils;
        this.productCode = productCode;
        targetRole = cpUtils.createRole(productCode, roleCode, roleName);
    }

    public RoleBuilder setCapabilityMap(Map<String, Attribute> capabilityMap) {
        this.capabilityMap = capabilityMap;
        return this;
    }

    public RoleBuilder setRoleRedirect(String redirectCode) {
        this.redirectCode = redirectCode;
        return this;
    }

    public RoleBuilder addView(String capabilityCode) {
        return addCapability(capabilityCode, CapabilityMode.VIEW);
    }

    public RoleBuilder addCapability(String capabilityCode, CapabilityMode... capModes) {
        roleCapabilities.put(capabilityCode, capModes);
        return this;
    }

    public BaseEntity build() throws RoleException {
        if(capabilityMap == null) {
            throw new RoleException("Capability Map not set. Try using setCapabilityMap(Map<String, Attribute> capabilityMap) before building.");
        }

        cpUtils.setRoleRedirect(productCode, targetRole, redirectCode);

        for(String capabilityCode : roleCapabilities.keySet()) {
            cpUtils.addCapabilityToBaseEntity(productCode, targetRole, fetch(capabilityCode), roleCapabilities.get(capabilityCode));
        }

        return targetRole;
    }

	private Attribute fetch(String attrCode) throws ItemNotFoundException {
		Attribute attribute = capabilityMap.get(attrCode);
		if(attribute == null) {
			log.error("Could not find capability in map: " + attrCode);
			throw new ItemNotFoundException("capability map", attrCode);
		}
		return attribute;
	}
}
