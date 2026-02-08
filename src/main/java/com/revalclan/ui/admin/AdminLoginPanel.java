package com.revalclan.ui.admin;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.admin.AdminAuthResponse;
import com.revalclan.ui.constants.UIConstants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

@Slf4j
public class AdminLoginPanel extends JPanel {
	private final RevalApiService apiService;
	private final Client client;
	private final Consumer<AdminAuthResponse.AdminAuthData> onSuccess;
	private final Runnable onCancel;

	private JTextField codeField;
	private JButton loginButton;
	private JLabel errorLabel;
	@Getter private String enteredCode;

	public AdminLoginPanel(RevalApiService apiService, Client client,
	                       Consumer<AdminAuthResponse.AdminAuthData> onSuccess, Runnable onCancel) {
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
		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.setBackground(UIConstants.BACKGROUND);

		// Lock emoji
		JLabel icon = new JLabel("\uD83D\uDD12");
		icon.setFont(icon.getFont().deriveFont(28f));
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel title = new JLabel("ADMIN LOGIN");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.ACCENT_GOLD);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = new JLabel("Enter your admin code");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Input
		codeField = new JTextField(20);
		codeField.setFont(FontManager.getRunescapeSmallFont());
		codeField.setBackground(UIConstants.CARD_BG);
		codeField.setForeground(UIConstants.TEXT_PRIMARY);
		codeField.setCaretColor(UIConstants.TEXT_PRIMARY);
		codeField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(UIConstants.BORDER_COLOR, 1),
			BorderFactory.createEmptyBorder(8, 12, 8, 12)
		));
		codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		codeField.addActionListener(e -> performLogin());

		// Error
		errorLabel = new JLabel(" ");
		errorLabel.setFont(FontManager.getRunescapeSmallFont());
		errorLabel.setForeground(UIConstants.ERROR_COLOR);
		errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		errorLabel.setVisible(false);

		// Buttons
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		buttons.setBackground(UIConstants.BACKGROUND);
		buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

		loginButton = createButton("Login", UIConstants.ACCENT_GOLD, Color.BLACK);
		loginButton.addActionListener(e -> performLogin());

		JButton cancelButton = createButton("Cancel", UIConstants.CARD_BG, UIConstants.TEXT_PRIMARY);
		cancelButton.addActionListener(e -> onCancel.run());

		buttons.add(loginButton);
		buttons.add(cancelButton);

		center.add(icon);
		center.add(Box.createRigidArea(new Dimension(0, 10)));
		center.add(title);
		center.add(Box.createRigidArea(new Dimension(0, 6)));
		center.add(hint);
		center.add(Box.createRigidArea(new Dimension(0, 16)));
		center.add(codeField);
		center.add(Box.createRigidArea(new Dimension(0, 6)));
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
		String code = codeField.getText().trim();
		if (code.isEmpty()) {
			showError("Please enter an admin code");
			return;
		}

		loginButton.setEnabled(false);
		errorLabel.setVisible(false);
		enteredCode = code;

		long accountHash = client != null ? client.getAccountHash() : -1;
		String osrsNickname = client != null && client.getLocalPlayer() != null
			? client.getLocalPlayer().getName() : null;

		apiService.adminLogin(code,
			accountHash != -1 ? accountHash : null,
			osrsNickname,
			response -> SwingUtilities.invokeLater(() -> {
				if (response.getData() != null && response.getData().isAuthenticated()) {
					onSuccess.accept(response.getData());
				} else {
					showError("Authentication failed");
					loginButton.setEnabled(true);
					enteredCode = null;
				}
			}),
			error -> SwingUtilities.invokeLater(() -> {
				showError(error.getMessage() != null ? error.getMessage() : "Login failed");
				loginButton.setEnabled(true);
				enteredCode = null;
			})
		);
	}

	private void showError(String message) {
		errorLabel.setText(message);
		errorLabel.setVisible(true);
	}
}
