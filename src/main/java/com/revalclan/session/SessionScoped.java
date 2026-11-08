package com.revalclan.session;

/** Client-thread state that must not cross a logout or plugin restart. */
public interface SessionScoped {
	void reset();
}
