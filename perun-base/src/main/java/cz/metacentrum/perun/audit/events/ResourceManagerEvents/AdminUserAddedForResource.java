package cz.metacentrum.perun.audit.events.ResourceManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.User;

public class AdminUserAddedForResource extends AuditEvent {

	private final User user;
	private final Resource resource;
	private final String message;

	public AdminUserAddedForResource(User user, Resource resource) {
		this.user = user;
		this.resource = resource;
		this.message = String.format("%s was added as admin of %s.", user, resource);
	}

	@Override
	public String getMessage() {
		return message;
	}

	public User getUser() {
		return user;
	}

	public Resource getResource() {
		return resource;
	}

	@Override
	public String toString() {
		return message;
	}
}
