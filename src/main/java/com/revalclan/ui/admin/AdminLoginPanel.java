package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Admin access for the linked account. There is nothing to type: the plugin
 * sends the logged-in account's link token and the server decides whether that
 * Reval account is an admin.
 */
public class AdminLoginPanel extends JPanel {
	private final RevalApiService apiService;
	private final Client client;
	private final AdminManager adminManager;
	private final Runnable onSuccess;
	private final Runnable onCancel;

	private JButton loginButton;
	private JLabel errorLabel;
	private AdminManager.Session attempt;

	public AdminLoginPanel(RevalApiService apiService, Client client, AdminManager adminManager,
	                       Runnable onSuccess, Runnable onCancel) {
		this.apiService = apiService;
		this.client = client;
		this.adminManager = adminManager;
		this.onSuccess = onSuccess;
		this.onCancel = onCancel;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(40, 30, 30, 30));
		buildUI();
	}

	private void buildUI() {
		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.setBackground(UIConstants.BACKGROUND);

		// Lock emoji
		JLabel icon = new JLabel("🔒");
		icon.setFont(icon.getFont().deriveFont(28f));
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel title = new PanelTitle("ADMIN ACCESS");
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = Labels.wrapped("Admin access uses the Reval account linked to this game account. "
				+ "Create the Link code from your own member record in the Reval admin console (Link my RuneLite plugin), "
				+ "paste it into the 'Link code' setting of this plugin, then check your access.",
			FontManager.getRunescapeSmallFont(), UIConstants.TEXT_SECONDARY, 170);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Error
		errorLabel = Labels.wrapped(" ", FontManager.getRunescapeSmallFont(), UIConstants.ERROR_COLOR, 170);
		errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		errorLabel.setVisible(false);

		// Buttons
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		buttons.setBackground(UIConstants.BACKGROUND);
		buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

		loginButton = createButton("Check access", UIConstants.ACCENT_GOLD, Color.BLACK);
		loginButton.setPreferredSize(new Dimension(110, 28));
		loginButton.addActionListener(e -> performLogin());

		JButton cancelButton = createButton("Cancel", UIConstants.CARD_BG, UIConstants.TEXT_PRIMARY);
		cancelButton.addActionListener(e -> onCancel.run());

		buttons.add(loginButton);
		buttons.add(cancelButton);

		center.add(icon);
		center.add(Box.createRigidArea(new Dimension(0, 10)));
		center.add(title);
		center.add(Box.createRigidArea(new Dimension(0, 10)));
		center.add(hint);
		center.add(Box.createRigidArea(new Dimension(0, 16)));
		center.add(errorLabel);
		center.add(Box.createRigidArea(new Dimension(0, 16)));
		center.add(buttons);

		add(center, BorderLayout.CENTER);
	}

	private JButton createButton(String text, Color bg, Color fg) {
		JButton btn = new JButton(text);
		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setForeground(fg);
		btn.setBackground(bg);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setPreferredSize(new Dimension(90, 28));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}

	private void performLogin() {
		loginButton.setEnabled(false);
		errorLabel.setVisible(false);
		final AdminManager.Session session = adminManager.begin();
		attempt = session;

		long accountHash = client != null ? client.getAccountHash() : -1;
		String osrsNickname = client != null && client.getLocalPlayer() != null
			? client.getLocalPlayer().getName() : null;

		apiService.adminLogin(
			accountHash != -1 ? accountHash : null,
			osrsNickname,
			response -> SwingUtilities.invokeLater(() -> {
				if (attempt != session || !session.isCurrent()) return;
				attempt = null;
				if (response.getData() != null && adminManager.complete(session, response.getData())) {
					onSuccess.run();
				} else {
					showError("Access denied. This Reval account has no admin permissions.");
					loginButton.setEnabled(true);
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				if (attempt != session || !session.isCurrent()) return;
				attempt = null;
				showError(error.getMessage() != null ? error.getMessage() : "Admin check failed");
				loginButton.setEnabled(true);
			})
		);
	}

	/** Drops any admin check still in flight, e.g. after game logout. */
	public void invalidate() {
		attempt = null;
	}

	private void showError(String message) {
		errorLabel.setText("<html><div style='width:170px'>" + Labels.escapeHtml(message) + "</div></html>");
		errorLabel.setVisible(true);
	}
}
