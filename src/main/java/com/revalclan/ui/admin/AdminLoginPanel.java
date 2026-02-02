package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.ui.RevalIcons;
import com.revalclan.ui.constants.UIConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Admin login panel - prompts for admin code and authenticates
 */
@Slf4j
public class AdminLoginPanel extends JPanel {

	private final RevalApiService apiService;
	private final Client client;
	private final Consumer<AdminAuthResponse.AdminAuthData> onSuccess;
	private final Runnable onCancel;

	private JTextField codeField;
	private JButton loginButton;
	private JLabel errorLabel;
	private String enteredCode;
	
	/**
	 * Get the entered code (after successful login)
	 */
	public String getEnteredCode() {
		return enteredCode;
	}

	public AdminLoginPanel(RevalApiService apiService, Client client,
	                      Consumer<AdminAuthResponse.AdminAuthData> onSuccess,
	                      Runnable onCancel) {
		this.apiService = apiService;
		this.client = client;
		this.onSuccess = onSuccess;
		this.onCancel = onCancel;

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);
		setBorder(new EmptyBorder(40, 30, 30, 30));

		buildUI();
	}

	private void buildUI() {
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(UIConstants.BACKGROUND);
		centerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Icon
		JLabel icon = new JLabel(RevalIcons.createLockIcon(32, UIConstants.ACCENT_GOLD));
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Title
		JLabel title = new JLabel("ADMIN LOGIN");
		title.setFont(new Font("Segoe UI", Font.BOLD, 20));
		title.setForeground(UIConstants.ACCENT_GOLD);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Hint
		JLabel hint = new JLabel("Enter your admin code");
		hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
		hint.setForeground(UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Code input field
		codeField = new JTextField(20);
		codeField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		codeField.setBackground(UIConstants.CARD_BG);
		codeField.setForeground(UIConstants.TEXT_PRIMARY);
		codeField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
			BorderFactory.createEmptyBorder(8, 12, 8, 12)
		));
		codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		codeField.addActionListener(e -> performLogin());

		// Error label
		errorLabel = new JLabel(" ");
		errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
		errorLabel.setForeground(UIConstants.ERROR_COLOR);
		errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		errorLabel.setVisible(false);

		// Buttons panel
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		buttonsPanel.setBackground(UIConstants.BACKGROUND);
		buttonsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		loginButton = new JButton("Login");
		loginButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
		loginButton.setForeground(Color.BLACK);
		loginButton.setBackground(UIConstants.ACCENT_GOLD);
		loginButton.setFocusPainted(false);
		loginButton.setBorderPainted(false);
		loginButton.setPreferredSize(new Dimension(100, 32));
		loginButton.addActionListener(e -> performLogin());

		JButton cancelButton = new JButton("Cancel");
		cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		cancelButton.setForeground(UIConstants.TEXT_PRIMARY);
		cancelButton.setBackground(UIConstants.CARD_BG);
		cancelButton.setFocusPainted(false);
		cancelButton.setBorderPainted(false);
		cancelButton.setPreferredSize(new Dimension(100, 32));
		cancelButton.addActionListener(e -> onCancel.run());

		buttonsPanel.add(loginButton);
		buttonsPanel.add(cancelButton);

		// Assemble
		centerPanel.add(icon);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 12)));
		centerPanel.add(title);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		centerPanel.add(hint);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
		centerPanel.add(codeField);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		centerPanel.add(errorLabel);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
		centerPanel.add(buttonsPanel);

		add(centerPanel, BorderLayout.CENTER);
	}

	private void performLogin() {
		String code = codeField.getText().trim();
		if (code.isEmpty()) {
			showError("Please enter an admin code");
			return;
		}

		loginButton.setEnabled(false);
		errorLabel.setVisible(false);
		enteredCode = code; // Store the code

		long accountHash = client != null ? client.getAccountHash() : -1;
		String osrsNickname = client != null && client.getLocalPlayer() != null
			? client.getLocalPlayer().getName() : null;

		apiService.adminLogin(
			code,
			accountHash != -1 ? accountHash : null,
			osrsNickname,
			response -> {
				SwingUtilities.invokeLater(() -> {
					if (response.getData() != null && response.getData().isAuthenticated()) {
						onSuccess.accept(response.getData());
					} else {
						showError("Authentication failed");
						loginButton.setEnabled(true);
						enteredCode = null;
					}
				});
			},
			error -> {
				SwingUtilities.invokeLater(() -> {
					showError(error.getMessage() != null ? error.getMessage() : "Login failed");
					loginButton.setEnabled(true);
					enteredCode = null;
				});
			}
		);
	}

	private void showError(String message) {
		errorLabel.setText(message);
		errorLabel.setVisible(true);
	}
}
