package com.revalclan.ui.admin;

import com.revalclan.api.admin.AdminAuthResponse;
import lombok.Getter;
import lombok.Setter;

import javax.inject.Singleton;
import java.util.List;

/**
 * Manages admin session state (member code and permissions)
 */
@Singleton
@Getter
@Setter
public class AdminManager {
	private String memberCode;
	private List<String> permissions;
	private AdminAuthResponse.AdminAuthData authData;

	/**
	 * Check if admin is logged in
	 */
	public boolean isLoggedIn() {
		return memberCode != null && !memberCode.isEmpty();
	}

	/**
	 * Clear admin session
	 */
	public void logout() {
		memberCode = null;
		permissions = null;
		authData = null;
	}

	/**
	 * Set admin session data
	 */
	public void setSession(String memberCode, AdminAuthResponse.AdminAuthData authData) {
		this.memberCode = memberCode;
		this.authData = authData;
		// TODO: Implement permissions filtering in the future
		if (authData != null) {
			this.permissions = authData.getPermissions();
		}
	}
}
