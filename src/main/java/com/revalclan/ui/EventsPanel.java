package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.events.EventsResponse;
import com.revalclan.ui.components.EventCard;
import com.revalclan.ui.components.LoginPrompt;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanValidator;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class EventsPanel extends JPanel {
	private RevalApiService apiService;
	private Client client;

	private JPanel contentPanel;
	private JPanel eventsListPanel;
	private RefreshButton refreshButton;

	private String currentTab = "active";
	private List<EventsResponse.EventSummary> allEvents = new ArrayList<>();
	private long currentAccountHash = -1;
	private String currentPlayerName = null;

	private JButton upcomingTab;
	private JButton activeTab;

	public EventsPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(12, 10, 10, 10));

		showNotLoggedIn();
		add(contentPanel, BorderLayout.NORTH);
	}

	public void init(RevalApiService apiService, Client client) {
		this.apiService = apiService;
		this.client = client;
	}

	public void onLoggedIn() {
		if (allEvents.isEmpty() || isShowingLoginPrompt()) {
			checkAuthorizationAndLoad();
		}
	}

	public void onLoggedOut() {
		SwingUtilities.invokeLater(this::showNotLoggedIn);
	}

	public void refresh() {
		if (apiService != null) {
			refreshButton.setLoading(true);
			apiService.refreshEvents(this::onEventsLoaded, this::onError);
		}
	}

	private boolean isShowingLoginPrompt() {
		return contentPanel.getComponentCount() == 0 ||
			contentPanel.getComponent(0) instanceof LoginPrompt;
	}

	private void checkAuthorizationAndLoad() {
		if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getAccountHash() == -1) {
			showNotLoggedIn();
			return;
		}

		if (ClanValidator.validateClan(client)) {
			buildFullUI();
			currentAccountHash = client.getAccountHash();
			currentPlayerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
			loadEvents();
		} else {
			scheduleRetry(1, 10, client.getAccountHash());
		}
	}

	private void scheduleRetry(int attempt, int maxAttempts, long accountHash) {
		if (attempt > maxAttempts) {
			showNotLoggedIn();
			return;
		}

		Timer timer = new Timer(attempt * 1000, e -> {
			if (ClanValidator.validateClan(client)) {
				buildFullUI();
				currentAccountHash = accountHash;
				currentPlayerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
				loadEvents();
			} else {
				scheduleRetry(attempt + 1, maxAttempts, accountHash);
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void buildFullUI() {
		contentPanel.removeAll();

		// Header
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		header.add(new PanelTitle("EVENTS"), BorderLayout.WEST);
		refreshButton = new RefreshButton(this::refresh);
		header.add(refreshButton, BorderLayout.EAST);

		contentPanel.add(header);
		contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

		// Tab bar
		contentPanel.add(buildTabBar());
		contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

		// Events list
		eventsListPanel = new JPanel();
		eventsListPanel.setLayout(new BoxLayout(eventsListPanel, BoxLayout.Y_AXIS));
		eventsListPanel.setBackground(UIConstants.BACKGROUND);
		eventsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(eventsListPanel);

		showLoading();
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel buildTabBar() {
		JPanel tabBar = new JPanel(new GridLayout(1, 2, 4, 0));
		tabBar.setOpaque(false);
		tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		upcomingTab = createTabButton("Upcoming", "upcoming", UIConstants.ACCENT_BLUE);
		activeTab = createTabButton("Active", "active", UIConstants.ACCENT_GREEN);

		tabBar.add(upcomingTab);
		tabBar.add(activeTab);

		updateTabSelection();
		return tabBar;
	}

	private JButton createTabButton(String text, String tabId, Color accentColor) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				boolean isActive = currentTab.equals(tabId);
				g2d.setColor(isActive ? UIConstants.TAB_ACTIVE : getModel().isRollover() ? UIConstants.TAB_HOVER : UIConstants.CARD_BG);
				g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

				if (isActive) {
					g2d.setColor(accentColor);
					g2d.setStroke(new BasicStroke(2));
					g2d.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2);
				}

				g2d.dispose();
				super.paintComponent(g);
			}
		};

		btn.setFont(FontManager.getRunescapeSmallFont());
		btn.setForeground(currentTab.equals(tabId) ? accentColor : UIConstants.TEXT_SECONDARY);
		btn.setBackground(null);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(false);
		btn.setFocusPainted(false);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		btn.addActionListener(e -> {
			currentTab = tabId;
			updateTabSelection();
			displayEvents();
		});

		return btn;
	}

	private void updateTabSelection() {
		upcomingTab.setForeground(currentTab.equals("upcoming") ? UIConstants.ACCENT_BLUE : UIConstants.TEXT_SECONDARY);
		activeTab.setForeground(currentTab.equals("active") ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_SECONDARY);
		upcomingTab.repaint();
		activeTab.repaint();
	}

	private void loadEvents() {
		if (apiService == null) {
			showError("API service not initialized");
			return;
		}
		showLoading();
		apiService.fetchEvents(this::onEventsLoaded, this::onError);
	}

	private void onEventsLoaded(EventsResponse response) {
		SwingUtilities.invokeLater(() -> {
			if (refreshButton != null) refreshButton.setLoading(false);
			allEvents = response != null && response.getData() != null && response.getData().getEvents() != null
				? response.getData().getEvents() : new ArrayList<>();
			displayEvents();
		});
	}

	private void onError(Exception e) {
		SwingUtilities.invokeLater(() -> {
			if (refreshButton != null) refreshButton.setLoading(false);
			showError("Failed to load events: " + e.getMessage());
		});
	}

	private void displayEvents() {
		eventsListPanel.removeAll();

		List<EventsResponse.EventSummary> filtered = allEvents.stream()
			.filter(e -> {
				switch (currentTab) {
					case "upcoming": return e.isUpcoming();
					case "active": return e.isCurrentlyActive();
					default: return true;
				}
			})
			.sorted(getComparator())
			.collect(Collectors.toList());

		if (filtered.isEmpty()) {
			showEmptyState();
		} else {
			for (EventsResponse.EventSummary event : filtered) {
				EventCard card = new EventCard(event, event.isCurrentlyActive(), currentPlayerName, this::handleRegistration);
				card.setAlignmentX(Component.LEFT_ALIGNMENT);
				card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
				eventsListPanel.add(card);
			}
		}

		eventsListPanel.revalidate();
		eventsListPanel.repaint();
	}

	private Comparator<EventsResponse.EventSummary> getComparator() {
		if ("upcoming".equals(currentTab)) {
			return Comparator.comparing(EventsResponse.EventSummary::getStartDate);
		}
		return Comparator.comparing(EventsResponse.EventSummary::getEndDate);
	}

	private void handleRegistration(String eventId, boolean isRegister) {
		if (apiService == null || currentAccountHash == -1) {
			JOptionPane.showMessageDialog(this, "Please log in to register", "Not Logged In", JOptionPane.WARNING_MESSAGE);
			return;
		}

		String action = isRegister ? "register for" : "withdraw from";
		int confirm = JOptionPane.showConfirmDialog(this, "Do you want to " + action + " this event?",
			"Confirm", JOptionPane.YES_NO_OPTION);

		if (confirm != JOptionPane.YES_OPTION) return;

		if (isRegister) {
			apiService.registerForEvent(eventId, currentAccountHash,
				response -> showMessage(response.getMessage(), "Registration"),
				error -> showMessage("Failed: " + error.getMessage(), "Error"));
		} else {
			apiService.cancelEventRegistration(eventId, currentAccountHash,
				response -> showMessage(response.getMessage(), "Withdrawal"),
				error -> showMessage("Failed: " + error.getMessage(), "Error"));
		}
	}

	private void showMessage(String message, String title) {
		SwingUtilities.invokeLater(() -> {
			JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
			refresh();
		});
	}

	private void showLoading() {
		eventsListPanel.removeAll();

		JPanel wrapper = new JPanel(new GridBagLayout());
		wrapper.setOpaque(false);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

		JLabel label = new JLabel("Loading events...");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(UIConstants.TEXT_MUTED);

		wrapper.add(label);
		eventsListPanel.add(wrapper);
		eventsListPanel.revalidate();
		eventsListPanel.repaint();
	}

	private void showNotLoggedIn() {
		contentPanel.removeAll();
		contentPanel.add(new LoginPrompt("Events"));
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void showEmptyState() {
		String message = currentTab.equals("upcoming") ? "No upcoming events"
			: "No active events right now";

		JPanel wrapper = new JPanel(new GridBagLayout());
		wrapper.setOpaque(false);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

		JLabel label = new JLabel(message);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(UIConstants.TEXT_MUTED);

		wrapper.add(label);
		eventsListPanel.add(wrapper);
	}

	private void showError(String message) {
		eventsListPanel.removeAll();

		JPanel wrapper = new JPanel(new GridBagLayout());
		wrapper.setOpaque(false);
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

		JPanel errorPanel = new JPanel();
		errorPanel.setLayout(new BoxLayout(errorPanel, BoxLayout.Y_AXIS));
		errorPanel.setOpaque(false);
		errorPanel.setBorder(new EmptyBorder(40, 0, 40, 0));

		JLabel text = new JLabel(message);
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(UIConstants.TEXT_MUTED);
		text.setAlignmentX(Component.CENTER_ALIGNMENT);

		JButton retryBtn = new JButton("Retry");
		retryBtn.setFont(FontManager.getRunescapeSmallFont());
		retryBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		retryBtn.addActionListener(e -> loadEvents());

		errorPanel.add(text);
		errorPanel.add(Box.createRigidArea(new Dimension(0, 12)));
		errorPanel.add(retryBtn);

		wrapper.add(errorPanel);
		eventsListPanel.add(wrapper);
		eventsListPanel.revalidate();
		eventsListPanel.repaint();
	}
}
