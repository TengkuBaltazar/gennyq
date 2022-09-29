package life.genny.qwandaq.managers.capabilities.role;


import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.datatype.Capability;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.checked.RoleException;
import life.genny.qwandaq.exception.runtime.ItemNotFoundException;
import life.genny.qwandaq.managers.capabilities.CapabilitiesManager;
import life.genny.qwandaq.utils.CommonUtils;


import static life.genny.qwandaq.datatype.Capability.CapabilityMode.VIEW;

public class RoleBuilder {
    private static final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass());
    
    private CapabilitiesManager capManager;
    private RoleManager roleMan;

    private BaseEntity targetRole;
    
    private String productCode;

    private List<BaseEntity> inheritedRoles = new ArrayList<>();

    private List<String> childrenCodes = new ArrayList<>();

    /**
     * A map from Attribute (Capability) Code to Attribute (Capability)
     */
    private Map<String, Attribute> capabilityMap;

    /**
     * A map from Capability Code to Capabilities to add to the role for that Capability
     */
    private Map<String, Capability[]> roleCapabilities = new HashMap<>();

    private String redirectCode;

    // TODO: Implement this
    private String[] sidebarEventCodes;

    // TODO: Again I want to get rid of product code chains like this
    // TODO: Hopefully we can firm up how product codes are assigned to tokens
    public RoleBuilder(String roleCode, String roleName, String productCode) {
        this.capManager = Arc.container().select(CapabilitiesManager.class).get();
        this.roleMan = capManager.getRoleManager();
        this.productCode = productCode;
        targetRole = roleMan.createRole(productCode, roleCode, roleName);
    }

    /**
     * Set the {@link RoleBuilder#capabilityMap}
     * @param capData a 2D String array, where each element of the first array is of the form {Code, Name}
     * Example:
     * <pre>
     *{
     *  {"CAP_ADMIN", "Manipulate Admin"},
     *  {"CAP_TENANT", "Manipulate Tenant"}
     *}
     * </pre>
     * @return this RoleBuilder
     * 
     * @see {@link CapabilitiesManager#getCapabilityMap(String, String[][])}
     */
    public RoleBuilder setCapabilityMap(String[][] capData) {
        this.capabilityMap = capManager.getCapabilityMap(productCode, capData);
        return this;
    }

    public RoleBuilder setCapabilityMap(Map<String, Attribute> capabilityMap) {
        this.capabilityMap = capabilityMap;
        return this;
    }

    public RoleBuilder setRoleRedirect(String redirectCode) {
        this.redirectCode = redirectCode;
        return this;
    }

    public RoleBuilder inheritRole(BaseEntity... otherRoles) {
        inheritedRoles.addAll(Arrays.asList(otherRoles));
        return this;
    }

    public RoleBuilder addView(String capabilityCode) {
        return addCapability(capabilityCode, VIEW);
    }

    @Deprecated
    public RoleBuilder addCapability(String capabilityCode, Capability.CapabilityMode... capModes) {
        Capability[] capabilities = Arrays.asList(capModes).stream().map((Capability.CapabilityMode mode) -> new Capability(mode)).collect(Collectors.toList()).toArray(new Capability[0]);
        return addCapability(capabilityCode, capabilities);
    }

    public RoleBuilder addCapability(String capabilityCode, Capability... capabilities) {
        capabilityCode = CommonUtils.safeStripPrefix(capabilityCode);
        roleCapabilities.put(capabilityCode, capabilities);
        return this;
    }

    public RoleBuilder setSidebar(String... eventCodes) {
        this.sidebarEventCodes = eventCodes;
        return this;
    }

    public RoleBuilder addChildren(String... roleCodes) {
        this.childrenCodes.addAll(Arrays.asList(roleCodes));
        return this;
    }

    public BaseEntity build() throws RoleException {
        if(capabilityMap == null) {
            throw new RoleException("Capability Map not set. Try using setCapabilityMap(Map<String, Attribute> capabilityMap) before building.");
        }

        // Redirect
        roleMan.setRoleRedirect(productCode, targetRole, redirectCode);

        // Capabilities
        for(String capabilityCode : roleCapabilities.keySet()) {
            capManager.addCapabilityToBaseEntity(productCode, targetRole, fetch(capabilityCode), roleCapabilities.get(capabilityCode));
        }
        
        // Role inherits
        for(BaseEntity parentRole : this.inheritedRoles) {
            roleMan.inheritRole(productCode, targetRole, parentRole);
        }

        // Sidebar
        for(String sidebarCode : sidebarEventCodes) {
            
        }

        // Children
        roleMan.setChildren(productCode, targetRole, childrenCodes.toArray(new String[0]));

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
