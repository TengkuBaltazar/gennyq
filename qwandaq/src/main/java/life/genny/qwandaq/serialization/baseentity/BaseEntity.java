package life.genny.qwandaq.serialization.baseentity;

import java.time.LocalDateTime;

public class BaseEntity {
	
	private String dtype;
	
	private Long id;
	
	private LocalDateTime created;
	
	private String name;
	
	private String realm;
	
	private LocalDateTime updated;
	
	private String code;
	
	private Integer status;

	public BaseEntity(String dtype, Long id, LocalDateTime created, String name, String realm, LocalDateTime updated,
			String code, Integer status) {
		super();
		this.dtype = dtype;
		this.id = id;
		this.created = created;
		this.name = name;
		this.realm = realm;
		this.updated = updated;
		this.code = code;
		this.status = status;
	}

	public BaseEntity() {
	}

	public String getDtype() {
		return dtype;
	}

	public void setDtype(String dtype) {
		this.dtype = dtype;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public LocalDateTime getCreated() {
		return created;
	}

	public void setCreated(LocalDateTime created) {
		this.created = created;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public LocalDateTime getUpdated() {
		return updated;
	}

	public void setUpdated(LocalDateTime updated) {
		this.updated = updated;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

}
