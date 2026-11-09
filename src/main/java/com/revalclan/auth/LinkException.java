package com.revalclan.auth;

import java.io.IOException;

/**
 * A request could not be made because the account is not (or no longer)
 * linked, or because sending the credential would be unsafe. The message is
 * written for the player and never contains a token or a Link code.
 */
public class LinkException extends IOException {
	public enum Kind {
		NOT_LOGGED_IN("Log in to the game first, then try again."),
		NOT_LINKED(LinkTokenManager.LINK_INSTRUCTION),
		/** The server said so (invalid_plugin_credential): the link is gone and was removed. */
		EXPIRED("Your Reval link has expired or was revoked. Create a new Link code " + LinkTokenManager.CODE_SOURCE + " and paste it into the 'Link code' setting of the Reval Clan plugin."),
		/** Only this computer thinks the link is over; the stored link is kept in case the clock is what is wrong. */
		EXPIRED_LOCALLY("Your Reval link looks expired on this computer. If the date and time on this computer are wrong, set them to update automatically and log in again. Otherwise create a new Link code " + LinkTokenManager.CODE_SOURCE + " and paste it into the 'Link code' setting of the Reval Clan plugin."),
		CLOCK_OFF("The date or time on this computer is more than a day away from Reval's. Set your system date and time to update automatically, then log in again."),
		/** A 401 that is not a credential rejection: the link is kept. */
		REFUSED("Reval did not accept this request right now. Please try again in a moment. If it keeps happening, create a new Link code " + LinkTokenManager.CODE_SOURCE + "."),
		ACCOUNT_MISMATCH("The logged-in game account changed. Please try again."),
		UNTRUSTED_ORIGIN("Refusing to send credentials to an unexpected server."),
		INVALID_RESPONSE("Reval returned an unexpected link response. Update the plugin or try again later.");

		private final String message;

		Kind(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}
	}

	private final Kind kind;

	public LinkException(Kind kind) {
		super(kind.message);
		this.kind = kind;
	}

	public LinkException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}
}
