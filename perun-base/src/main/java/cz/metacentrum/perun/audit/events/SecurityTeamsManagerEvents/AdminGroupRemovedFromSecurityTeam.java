package cz.metacentrum.perun.audit.events.SecurityTeamsManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.SecurityTeam;

public class AdminGroupRemovedFromSecurityTeam extends AuditEvent {

	private final Group group;
	private final SecurityTeam securityTeam;
	private final String message;

	public AdminGroupRemovedFromSecurityTeam(Group group, SecurityTeam securityTeam) {
		this.group = group;
		this.securityTeam = securityTeam;
		this.message = String.format("%s was removed from security admins of %s.", group, securityTeam);
	}

	@Override
	public String getMessage() {
		return message;
	}

	public Group getGroup() {
		return group;
	}

	public SecurityTeam getSecurityTeam() {
		return securityTeam;
	}

	@Override
	public String toString() {
		return message;
	}
}
