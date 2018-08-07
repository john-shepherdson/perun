package cz.metacentrum.perun.audit.events.SecurityTeamsManagerEvents;

import cz.metacentrum.perun.audit.events.AuditEvent;
import cz.metacentrum.perun.core.api.SecurityTeam;

public class SecurityTeamUpdated extends AuditEvent {

	private final SecurityTeam securityTeam;
	private final String message;

	public SecurityTeamUpdated(SecurityTeam securityTeam) {
		this.securityTeam = securityTeam;
		this.message = String.format("%s was updated.", securityTeam);
	}

	@Override
	public String getMessage() {
		return message;
	}

	public SecurityTeam getSecurityTeam() {
		return securityTeam;
	}

	@Override
	public String toString() {
		return message;
	}
}
