package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.ui.components.GradientSeparator;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.UIAssetLoader;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RevalPanel extends PluginPanel {
	private static final String DISCORD_URL = "https://discord.gg/reval";
	private static final String WEBSITE_URL = "https://revalosrs.ee";

	private final CardLayout cardLayout;
	private final JPanel contentPanel;

	@Getter private final ProfilePanel profilePanel;
	@Getter private final RankingPanel rankingPanel;

	private JLabel infoButton;
	private JLabel discordIcon;
	private JLabel websiteIcon;

	private JButton profileTab;
	private String selectedTab = "PROFILE";

	public RevalPanel() {
		super(false);

		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		profilePanel = new ProfilePanel();
		rankingPanel = new RankingPanel();

		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);
		contentPanel.setBackground(UIConstants.BACKGROUND);

		contentPanel.add(profilePanel, "PROFILE");
		contentPanel.add(rankingPanel, "RANKING");

		JPanel header = createHeader();
		JPanel navBar = createNavBar();

		JPanel topSection = new JPanel(new BorderLayout());
		topSection.setBackground(UIConstants.BACKGROUND);
		topSection.add(header, BorderLayout.NORTH);
		topSection.add(navBar, BorderLayout.CENTER);

		add(topSection, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);

		selectTab("PROFILE");
	}

	private JPanel createNavBar() {
		JPanel navBar = new JPanel(new BorderLayout());
		navBar.setBackground(UIConstants.BACKGROUND);
		navBar.setBorder(new EmptyBorder(0, 6, 6, 6));

		profileTab = new JButton("Profile");
		profileTab.setFont(new Font("SansSerif", Font.BOLD, 11));
		profileTab.setFocusPainted(false);
		profileTab.setBorderPainted(false);
		profileTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		profileTab.setPreferredSize(new Dimension(0, 28));
		profileTab.setBackground(UIConstants.ACCENT_BLUE);
		profileTab.setForeground(Color.WHITE);
		profileTab.addActionListener(e -> selectTab("PROFILE"));

		navBar.add(profileTab, BorderLayout.CENTER);

		return navBar;
	}

	private JPanel createHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(6, 12, 8, 12));

		infoButton = new JLabel();
		infoButton.setToolTipText("Ranks & Points");
		infoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		infoButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					selectTab("RANKING");
				}
			}
		});

		JPanel infoButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		infoButtonPanel.setBackground(UIConstants.BACKGROUND);
		infoButtonPanel.setOpaque(false);
		infoButtonPanel.setPreferredSize(new Dimension(30, 22));
		infoButtonPanel.add(infoButton);

		JPanel socialPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
		socialPanel.setBackground(UIConstants.BACKGROUND);
		socialPanel.setOpaque(false);
		socialPanel.setBorder(new EmptyBorder(0, 0, 6, 0));

		discordIcon = createSocialIcon(DISCORD_URL, "Join our Discord");
		websiteIcon = createSocialIcon(WEBSITE_URL, "Visit our website");

		socialPanel.add(discordIcon);
		socialPanel.add(websiteIcon);

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setBackground(UIConstants.BACKGROUND);
		topRow.setOpaque(false);

		JPanel centerWrapper = new JPanel();
		centerWrapper.setLayout(new BoxLayout(centerWrapper, BoxLayout.X_AXIS));
		centerWrapper.setOpaque(false);
		centerWrapper.add(Box.createHorizontalGlue());
		centerWrapper.add(socialPanel);
		centerWrapper.add(Box.createHorizontalGlue());

		topRow.add(infoButtonPanel, BorderLayout.WEST);
		topRow.add(centerWrapper, BorderLayout.CENTER);

		JPanel rightPanel = new JPanel();
		rightPanel.setOpaque(false);
		rightPanel.setPreferredSize(new Dimension(30, 22));
		topRow.add(rightPanel, BorderLayout.EAST);

		PanelTitle titleLabel = new PanelTitle("REVAL CLAN", SwingConstants.CENTER);

		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(UIConstants.BACKGROUND);
		titlePanel.add(titleLabel, BorderLayout.CENTER);
		titlePanel.setBorder(new EmptyBorder(4, 0, 6, 0));

		header.add(topRow, BorderLayout.NORTH);
		header.add(titlePanel, BorderLayout.CENTER);
		header.add(new GradientSeparator(), BorderLayout.SOUTH);

		return header;
	}

	private void selectTab(String tabName) {
		selectedTab = tabName;
		updateNavStyles();
		cardLayout.show(contentPanel, tabName);
	}

	private void updateNavStyles() {
		if (profileTab != null) {
			boolean isProfile = "PROFILE".equals(selectedTab);
			profileTab.setBackground(isProfile ? UIConstants.ACCENT_BLUE : UIConstants.CARD_BG);
			profileTab.setForeground(isProfile ? Color.WHITE : UIConstants.TEXT_SECONDARY);
		}
	}

	private JLabel createSocialIcon(String url, String tooltip) {
		JLabel label = new JLabel();
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					LinkBrowser.browse(url);
				}
			}
		});
		return label;
	}

	public void showTab(String tabName) {
		selectTab(tabName);
	}

	public String getActiveTab() {
		return selectedTab;
	}

	public void init(RevalApiService apiService, Client client,
					 UIAssetLoader assetLoader, ItemManager itemManager, SpriteManager spriteManager) {
		if (assetLoader != null) {
			ImageIcon infoIcon = assetLoader.getIcon("info.png", 16);
			if (infoIcon != null && infoButton != null) {
				infoButton.setIcon(infoIcon);
			}

			ImageIcon discordImg = assetLoader.getIcon("discord.png", 22);
			if (discordImg != null && discordIcon != null) {
				discordIcon.setIcon(discordImg);
			}

			ImageIcon websiteImg = assetLoader.getIcon("website.png", 16);
			if (websiteImg != null && websiteIcon != null) {
				websiteIcon.setIcon(websiteImg);
			}
		}

		rankingPanel.init(apiService, itemManager, spriteManager);
		profilePanel.init(apiService, client, assetLoader);
	}

	public void onLoggedIn() {
		profilePanel.refresh();
	}

	public void onLoggedOut() {
		profilePanel.onLoggedOut();
	}
}
