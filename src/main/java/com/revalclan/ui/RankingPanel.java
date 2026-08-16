package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.components.*;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.ClanRankIconResolver;
import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ranking tab panel - displays clan ranks and point sources
 */
public class RankingPanel extends JPanel {
	private final JPanel contentPanel;
	private final JLabel loadingLabel;
	private final GridBagConstraints gbc;
	private int gridY = 0;

	private RevalApiService apiService;
	private ItemManager itemManager;
	private SpriteManager spriteManager;
	private ClanRankIconResolver rankIconResolver;

	public RankingPanel() {
		setLayout(new BorderLayout());
		setBackground(UIConstants.BACKGROUND);

		contentPanel = new JPanel(new GridBagLayout());
		contentPanel.setBackground(UIConstants.BACKGROUND);
		contentPanel.setBorder(new EmptyBorder(6, 2, 6, 2));

		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.insets = new Insets(1, 0, 1, 0);

		JPanel wrapper = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = super.getPreferredSize();
				if (getParent() != null) {
					size.width = getParent().getWidth();
				}
				return size;
			}
		};
		wrapper.setBackground(UIConstants.BACKGROUND);
		wrapper.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(UIConstants.BACKGROUND);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

		loadingLabel = new JLabel("Loading...");
		loadingLabel.setFont(FontManager.getRunescapeSmallFont());
		loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
		loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);

		showPlaceholder();
		add(scrollPane, BorderLayout.CENTER);
	}

	public void init(RevalApiService apiService, ItemManager itemManager, SpriteManager spriteManager,
					 ClanRankIconResolver rankIconResolver) {
		this.apiService = apiService;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.rankIconResolver = rankIconResolver;
		loadData();
	}

	public void refresh() {
		if (apiService != null) {
			apiService.clearCache();
			loadData();
		}
	}

	private void addComponent(JComponent comp) {
		gbc.gridy = gridY++;
		contentPanel.add(comp, gbc);
	}

	private void addSpacing(int height) {
		gbc.gridy = gridY++;
		gbc.insets = new Insets(height / 2, 0, height / 2, 0);
		contentPanel.add(Box.createVerticalStrut(height), gbc);
		gbc.insets = new Insets(1, 0, 1, 0);
	}

	private void showPlaceholder() {
		contentPanel.removeAll();
		gridY = 0;

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UIConstants.BACKGROUND);
		panel.setBorder(new EmptyBorder(30, 10, 10, 10));

		JLabel title = new JLabel("RANKING");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(UIConstants.ACCENT_GOLD);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel subtitle = new JLabel("Ranks & Points");
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setForeground(UIConstants.TEXT_SECONDARY);
		subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel hint = new JLabel("Loading data...");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(UIConstants.TEXT_SECONDARY);
		hint.setAlignmentX(Component.CENTER_ALIGNMENT);

		panel.add(title);
		panel.add(Box.createRigidArea(new Dimension(0, 4)));
		panel.add(subtitle);
		panel.add(Box.createRigidArea(new Dimension(0, 10)));
		panel.add(hint);

		addComponent(panel);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void showLoading() {
		contentPanel.removeAll();
		gridY = 0;

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		panel.setBackground(UIConstants.BACKGROUND);
		panel.setBorder(new EmptyBorder(30, 0, 0, 0));
		panel.add(loadingLabel);

		addComponent(panel);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void showError() {
		contentPanel.removeAll();
		gridY = 0;

		JLabel label = new JLabel("Something went wrong");
		label.setForeground(UIConstants.TEXT_SECONDARY);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(30, 0, 0, 0));
		addComponent(label);

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void loadData() {
		showLoading();
		apiService.fetchPoints(
			response -> SwingUtilities.invokeLater(() -> buildContent(response)),
			error -> SwingUtilities.invokeLater(this::showError)
		);
	}

	private void buildContent(PointsResponse response) {
		contentPanel.removeAll();
		gridY = 0;

		if (response == null || response.getData() == null) {
			showError();
			return;
		}

		PointsResponse.PointsData data = response.getData();

		// Ranks section
		if (data.getRanks() != null && !data.getRanks().isEmpty()) {
			addComponent(createSectionHeader("RANKS", "Clan rank progression"));

			for (PointsResponse.Rank rank : data.getRanks()) {
				addComponent(new RankCard(rank, spriteManager, rankIconResolver));
			}
		}

		// Point sources section
		addSpacing(12);
		addComponent(createSectionHeader("POINT SOURCES", "Ways to earn points"));

		if (data.getPointSources() != null) {
			for (Map.Entry<String, List<PointsResponse.PointSource>> entry : data.getPointSources().entrySet()) {
				String category = entry.getKey();
				List<PointsResponse.PointSource> sources = entry.getValue();

				if (sources == null || sources.isEmpty() || category.equals("UNTRADEABLE_DROPS")) continue;

				JPanel content = createPointSourcesPanel(sources, category);
				String displayName = formatCategoryName(category);

				CollapsibleSection section = new CollapsibleSection(displayName, null, content, false);
				Integer itemId = sources.get(0).getMetadata() != null ? sources.get(0).getMetadata().getItemId() : null;
				if (itemId != null) {
					loadSectionIcon(itemId, section);
				} else if (category.toUpperCase().equals("MISC")) {
					// Misc has no item id — use the stats side-tab sprite (total level icon)
					loadSpriteIcon(SpriteID.TAB_STATS, section);
				}
				addComponent(section);
			}
		}

		// Untradeable drops
		List<PointsResponse.PointSource> untradeables = data.getPointSources() != null 
			? data.getPointSources().get("UNTRADEABLE_DROPS") : null;
		if (untradeables != null && !untradeables.isEmpty()) {
			JPanel content = createUntradeableDropsPanel(untradeables);
			CollapsibleSection section = new CollapsibleSection("Untradeable Drops", null, content, false);
			Integer itemId = untradeables.get(0).getMetadata() != null ? untradeables.get(0).getMetadata().getItemId() : null;
			if (itemId != null) loadSectionIcon(itemId, section);
			addComponent(section);
		}

		addSpacing(16);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private JPanel createVerticalPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(UIConstants.BACKGROUND);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JPanel createSectionHeader(String title, String subtitle) {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(UIConstants.BACKGROUND);
		header.setBorder(new EmptyBorder(6, 2, 4, 2));

    JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(UIConstants.ACCENT_GOLD);
		textPanel.add(titleLabel);

		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subtitleLabel.setForeground(UIConstants.TEXT_SECONDARY);
		textPanel.add(subtitleLabel);

		header.add(textPanel, BorderLayout.WEST);
		return header;
	}

	private void loadSpriteIcon(int spriteId, CollapsibleSection section) {
		if (spriteManager == null) return;
		spriteManager.getSpriteAsync(spriteId, 0, sprite -> {
			if (sprite != null) {
				SwingUtilities.invokeLater(() -> {
					BufferedImage img = sprite;
					if (img.getWidth() > 22 || img.getHeight() > 22) {
						double scale = Math.min(22.0 / img.getWidth(), 22.0 / img.getHeight());
						img = ImageUtil.resizeImage(img,
							Math.max(1, (int) Math.round(img.getWidth() * scale)),
							Math.max(1, (int) Math.round(img.getHeight() * scale)));
					}
					section.setIcon(new ImageIcon(ImageUtil.resizeCanvas(img, 24, 24)));
				});
			}
		});
	}

	private void loadSectionIcon(int itemId, CollapsibleSection section) {
		if (itemManager == null) return;
		try {
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.onLoaded(() -> SwingUtilities.invokeLater(() -> 
				section.setIcon(new ImageIcon(ImageUtil.resizeImage(img, 24, 24)))
			));
		} catch (Exception ignored) {}
	}

	private JPanel createPointSourcesPanel(List<PointsResponse.PointSource> sources, String category) {
		JPanel panel = createVerticalPanel();
		panel.setBorder(new EmptyBorder(2, 0, 2, 0));

		boolean showIcons = !category.toUpperCase().equals("VALUABLE_PVM_DROPS") 
			&& !category.toUpperCase().equals("PETS");

		for (PointsResponse.PointSource source : sources) {
			PointSourceCard card = showIcons 
				? new PointSourceCard(source, itemManager)
				: new PointSourceCard(source.getName(), source.getDescription(), source.getPointsDisplay(), null);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(card);
			panel.add(Box.createRigidArea(new Dimension(0, 1)));
		}
		return panel;
	}

	private JPanel createUntradeableDropsPanel(List<PointsResponse.PointSource> sources) {
		JPanel panel = createVerticalPanel();
		panel.setBorder(new EmptyBorder(2, 0, 2, 0));

		Map<String, List<PointsResponse.PointSource>> grouped = sources.stream()
			.filter(s -> s.getMetadata() != null && s.getMetadata().getCategory() != null)
			.collect(Collectors.groupingBy(s -> s.getMetadata().getCategory()));

		for (Map.Entry<String, List<PointsResponse.PointSource>> entry : grouped.entrySet()) {
			List<PointsResponse.PointSource> items = entry.getValue();
			if (items.isEmpty()) continue;

			// Category header
			JLabel header = new JLabel(entry.getKey());
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setForeground(UIConstants.ACCENT_GOLD);
			header.setBorder(new EmptyBorder(4, 0, 2, 0));
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(header);

			// Items
			for (PointsResponse.PointSource source : items) {
				PointSourceCard card = PointSourceCard.forUntradeable(source, itemManager);
				card.setAlignmentX(Component.LEFT_ALIGNMENT);
				panel.add(card);
				panel.add(Box.createRigidArea(new Dimension(0, 1)));
			}
			panel.add(Box.createRigidArea(new Dimension(0, 4)));
		}
		return panel;
	}

	private String formatCategoryName(String name) {
		if (name == null) return name;
		String lower = name.toLowerCase();
		
		if (lower.equals("valuable_pvm_drops")) return "Valuable Drops";
		if (lower.equals("pets")) return "Pets";
		if (lower.equals("milestones")) return "Milestones";
		if (lower.equals("collection_log")) return "Collection Log";
		if (lower.equals("combat_achievements")) return "Combat Achievements";

		// Default: capitalize words
		StringBuilder result = new StringBuilder();
		for (String word : name.split("[_\\s]+")) {
			if (result.length() > 0) result.append(" ");
			if (!word.isEmpty()) {
				result.append(Character.toUpperCase(word.charAt(0)));
				if (word.length() > 1) result.append(word.substring(1).toLowerCase());
			}
		}
		return result.toString();
	}
}
