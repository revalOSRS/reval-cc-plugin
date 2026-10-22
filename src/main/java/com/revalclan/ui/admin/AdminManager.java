package com.revalclan.ui.admin;

import com.revalclan.api.admin.AdminAuthResponse;
import lombok.Getter;

/**
 * Manages admin session state (member code and auth data)
 */
@Getter
public class AdminManager {
	private String memberCode;
	private AdminAuthResponse.AdminAuthData authData;

	/**
	 * Check if admin is logged in
	 */
	public boolean isLoggedIn() {
		return memberCode != null && !memberCode.isEmpty();
	}

	/**
	 * Set admin session data
	 */
	public void setSession(String memberCode, AdminAuthResponse.AdminAuthData authData) {
		this.memberCode = memberCode;
		this.authData = authData;
		// TODO: Implement permissions filtering (authData.getPermissions()) in the future
	}
}
