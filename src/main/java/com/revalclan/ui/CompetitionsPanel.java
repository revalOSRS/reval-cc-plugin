package com.revalclan.ui;

import com.revalclan.api.RevalApiService;
import com.revalclan.api.competitions.*;
import com.revalclan.ui.components.BackButton;
import com.revalclan.ui.components.PanelTitle;
import com.revalclan.ui.components.RefreshButton;
import com.revalclan.ui.constants.UIConstants;
import net.runelite.api.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * Competitions panel - displays active votes, competitions, and allows viewing standings
 */
public class CompetitionsPanel extends JPanel {
    private RevalApiService apiService;
    private Client client;
    private RefreshButton refreshButton;

    private final CardLayout cardLayout;
    private final JPanel cardContainer;

    private final JPanel listViewPanel;
    private final JPanel contentPanel;
    private final JLabel loadingLabel;

    private final JPanel detailViewPanel;

    private List<VotesResponse.Vote> activeVotes = new ArrayList<>();
    private List<CompetitionsResponse.Competition> activeCompetitions = new ArrayList<>();
    private List<CompetitionsResponse.Competition> scheduledCompetitions = new ArrayList<>();
    private java.util.Map<String, String> myVotes = new java.util.HashMap<>();

    private static final String LIST_VIEW = "LIST";
    private static final String DETAIL_VIEW = "DETAIL";

    public CompetitionsPanel() {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);

        cardLayout = new CardLayout();
        cardContainer = new JPanel(cardLayout);
        cardContainer.setBackground(UIConstants.BACKGROUND);

        listViewPanel = new JPanel(new BorderLayout());
        listViewPanel.setBackground(UIConstants.BACKGROUND);

        JPanel header = createHeader();
        listViewPanel.add(header, BorderLayout.NORTH);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIConstants.BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel contentWrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (getParent() != null) {
                    size.width = getParent().getWidth();
                }
                return size;
            }
        };
        contentWrapper.setBackground(UIConstants.BACKGROUND);
        contentWrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setBackground(UIConstants.BACKGROUND);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

        listViewPanel.add(scrollPane, BorderLayout.CENTER);

        loadingLabel = new JLabel("Loading competitions...");
        loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        detailViewPanel = new JPanel(new BorderLayout());
        detailViewPanel.setBackground(UIConstants.BACKGROUND);

        cardContainer.add(listViewPanel, LIST_VIEW);
        cardContainer.add(detailViewPanel, DETAIL_VIEW);

        add(cardContainer, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.CARD_BG);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        PanelTitle title = new PanelTitle("Competitions", UIConstants.TEXT_PRIMARY);

        refreshButton = new RefreshButton(Color.WHITE, UIConstants.TAB_HOVER);
        refreshButton.setRefreshAction(this::refresh);

        header.add(title, BorderLayout.WEST);
        header.add(refreshButton, BorderLayout.EAST);

        return header;
    }

    public void init(RevalApiService apiService, Client client) {
        this.apiService = apiService;
        this.client = client;
        loadData(true);
    }

    public void refresh() {
        loadData(false); // Don't show loading on refresh
    }

    private void setRefreshLoading(boolean loading) {
        if (refreshButton != null) {
            refreshButton.setEnabled(!loading);
            refreshButton.setText(loading ? "Loading..." : "Refresh");
        }
    }

    private void fetchMyVotes(List<VotesResponse.Vote> votes) {
        if (client == null || client.getAccountHash() == -1) return;
        
        myVotes.clear();
        for (VotesResponse.Vote vote : votes) {
            apiService.fetchMyVote(vote.getId(), client.getAccountHash(),
                response -> {
                    if (response.getData() != null && response.getData().getHasVoted() && response.getData().getOptionId() != null) {
                        myVotes.put(vote.getId(), response.getData().getOptionId());
                        SwingUtilities.invokeLater(this::buildContent);
                    }
                },
                error -> {}
            );
        }
    }

    private void loadData(boolean showLoadingState) {
        if (apiService == null) return;

        if (showLoadingState) {
            showLoading();
        }
        
        SwingUtilities.invokeLater(() -> setRefreshLoading(true));

        final boolean[] votesLoaded = {false};
        final boolean[] activeLoaded = {false};
        final boolean[] scheduledLoaded = {false};

        Runnable checkComplete = () -> {
            if (votesLoaded[0] && activeLoaded[0] && scheduledLoaded[0]) {
                SwingUtilities.invokeLater(() -> {
                    setRefreshLoading(false);
                    buildContent();
                });
            }
        };

        apiService.fetchVotes(
            response -> {
                if (response.getData() != null && response.getData().getVotes() != null) {
                    activeVotes = response.getData().getVotes();
                    fetchMyVotes(activeVotes);
                } else {
                    activeVotes = new ArrayList<>();
                }
                votesLoaded[0] = true;
                checkComplete.run();
            },
            error -> {
                activeVotes = new ArrayList<>();
                votesLoaded[0] = true;
                checkComplete.run();
            }
        );

        apiService.fetchActiveCompetitions(
            response -> {
                if (response.getData() != null && response.getData().getCompetitions() != null) {
                    activeCompetitions = response.getData().getCompetitions();
                } else {
                    activeCompetitions = new ArrayList<>();
                }
                activeLoaded[0] = true;
                checkComplete.run();
            },
            error -> {
                activeCompetitions = new ArrayList<>();
                activeLoaded[0] = true;
                checkComplete.run();
            }
        );

        apiService.fetchScheduledCompetitions(
            response -> {
                if (response.getData() != null && response.getData().getCompetitions() != null) {
                    scheduledCompetitions = response.getData().getCompetitions();
                } else {
                    scheduledCompetitions = new ArrayList<>();
                }
                scheduledLoaded[0] = true;
                checkComplete.run();
            },
            error -> {
                scheduledCompetitions = new ArrayList<>();
                scheduledLoaded[0] = true;
                checkComplete.run();
            }
        );
    }

    private void showLoading() {
        contentPanel.removeAll();
        contentPanel.add(Box.createVerticalStrut(30));
        contentPanel.add(loadingLabel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void buildContent() {
        contentPanel.removeAll();

        boolean hasContent = false;

        // Active Votes Section
        if (!activeVotes.isEmpty()) {
            hasContent = true;
            contentPanel.add(createSectionTitle("Active Votes"));
            contentPanel.add(Box.createVerticalStrut(6));

            for (VotesResponse.Vote vote : activeVotes) {
                contentPanel.add(createVoteCard(vote));
                contentPanel.add(Box.createVerticalStrut(8));
            }
            contentPanel.add(Box.createVerticalStrut(12));
        }

        if (!activeCompetitions.isEmpty()) {
            hasContent = true;
            contentPanel.add(createSectionTitle("Active Competitions"));
            contentPanel.add(Box.createVerticalStrut(6));

            for (CompetitionsResponse.Competition comp : activeCompetitions) {
                contentPanel.add(createCompetitionCard(comp, true));
                contentPanel.add(Box.createVerticalStrut(8));
            }
            contentPanel.add(Box.createVerticalStrut(12));
        }

        if (!scheduledCompetitions.isEmpty()) {
            hasContent = true;
            contentPanel.add(createSectionTitle("Upcoming Competitions"));
            contentPanel.add(Box.createVerticalStrut(6));

            for (CompetitionsResponse.Competition comp : scheduledCompetitions) {
                contentPanel.add(createCompetitionCard(comp, false));
                contentPanel.add(Box.createVerticalStrut(8));
            }
        }

        if (!hasContent) {
            contentPanel.add(Box.createVerticalStrut(30));
            JLabel emptyLabel = new JLabel("No active competitions or votes");
            emptyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            emptyLabel.setForeground(UIConstants.TEXT_SECONDARY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(emptyLabel);
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createSectionTitle(String text) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(UIConstants.TEXT_PRIMARY);
        panel.add(label);

        return panel;
    }

    private JPanel createVoteCard(VotesResponse.Vote vote) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UIConstants.CARD_BG);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        JLabel titleLabel = new JLabel(vote.getTitle());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        if (vote.getDescription() != null && !vote.getDescription().isEmpty()) {
            card.add(Box.createVerticalStrut(4));
            JLabel descLabel = new JLabel("<html><body style='width: 200px'>" + vote.getDescription() + "</body></html>");
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descLabel.setForeground(UIConstants.TEXT_SECONDARY);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(descLabel);
        }

        card.add(Box.createVerticalStrut(6));
        String timeRemaining = getTimeRemaining(vote.getVoteEndDate());
        JLabel timeLabel = new JLabel("Voting ends: " + timeRemaining);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(UIConstants.TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(timeLabel);

        JLabel votesLabel = new JLabel(vote.getTotalVotes() + " total votes");
        votesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        votesLabel.setForeground(UIConstants.TEXT_MUTED);
        votesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(votesLabel);

        if (vote.getOptions() != null && !vote.getOptions().isEmpty()) {
            card.add(Box.createVerticalStrut(10));
            
            String myVotedOptionId = myVotes.get(vote.getId());

            for (VotesResponse.VoteOption option : vote.getOptions()) {
                boolean isMyVote = option.getId() != null && option.getId().equals(myVotedOptionId);
                card.add(createVoteOptionRow(vote, option, isMyVote));
                card.add(Box.createVerticalStrut(4));
            }
        }

        return wrapInRoundedPanel(card);
    }

    private JPanel createVoteOptionRow(VotesResponse.Vote vote, VotesResponse.VoteOption option, boolean isMyVote) {
        final Color defaultBg = isMyVote ? new Color(46, 125, 50, 40) : UIConstants.BACKGROUND;
        
        JPanel row = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                
                if (isMyVote) {
                    g2d.setColor(UIConstants.ACCENT_GREEN);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 4, 4);
                }
                
                g2d.dispose();
            }
        };
        row.setOpaque(false);
        row.setBackground(defaultBg);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String displayName = isMyVote ? "✓ " + option.getTemplateName() : option.getTemplateName();
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(new Font("Segoe UI", isMyVote ? Font.BOLD : Font.PLAIN, 11));
        nameLabel.setForeground(isMyVote ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY);

        int percentage = vote.getTotalVotes() > 0 
            ? (int) Math.round((option.getVoteCount() * 100.0) / vote.getTotalVotes())
            : 0;
        JLabel countLabel = new JLabel(option.getVoteCount() + " (" + percentage + "%)");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        countLabel.setForeground(isMyVote ? UIConstants.ACCENT_GREEN : UIConstants.ACCENT_BLUE);

        row.add(nameLabel, BorderLayout.WEST);
        row.add(countLabel, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(UIConstants.CARD_HOVER);
                row.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(defaultBg);
                row.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    castVote(vote.getId(), option.getId());
                }
            }
        });

        return row;
    }

    private void castVote(String voteId, String optionId) {
        if (apiService == null || client == null || client.getAccountHash() == -1) {
            JOptionPane.showMessageDialog(this, "Please log in to vote", "Not Logged In", JOptionPane.WARNING_MESSAGE);
            return;
        }

        apiService.castVote(voteId, optionId, client.getAccountHash(),
            response -> {
                SwingUtilities.invokeLater(() -> {
                    String message = response.getData() != null && response.getData().getPreviousOptionId() != null 
                        ? "Vote changed successfully!" 
                        : "Vote cast successfully!";
                    JOptionPane.showMessageDialog(this, message, "Voted", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                });
            },
            error -> {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to cast vote: " + error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        );
    }

    private JPanel createCompetitionCard(CompetitionsResponse.Competition comp, boolean isActive) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UIConstants.CARD_BG);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLabel = new JLabel(comp.getName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(isActive ? UIConstants.ACCENT_GREEN : UIConstants.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        if (comp.getDescription() != null && !comp.getDescription().isEmpty()) {
            card.add(Box.createVerticalStrut(4));
            JLabel descLabel = new JLabel("<html><body style='width: 200px'>" + comp.getDescription() + "</body></html>");
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descLabel.setForeground(UIConstants.TEXT_SECONDARY);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(descLabel);
        }

        card.add(Box.createVerticalStrut(6));
        String timeText = isActive 
            ? "Ends: " + getTimeRemaining(comp.getEndDate())
            : "Starts: " + getTimeRemaining(comp.getStartDate());
        JLabel timeLabel = new JLabel(timeText);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(UIConstants.TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(timeLabel);

        if (comp.getParticipantCount() != null && comp.getParticipantCount() > 0) {
            JLabel participantsLabel = new JLabel(comp.getParticipantCount() + " participants");
            participantsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            participantsLabel.setForeground(UIConstants.TEXT_MUTED);
            participantsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(participantsLabel);
        }

        if (isActive && comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
            card.add(Box.createVerticalStrut(8));
            card.add(createLeaderboardPreview(comp.getLeaderboard(), comp.getCompetitionType()));
        }

        if (comp.getRewardConfig() != null && !comp.getRewardConfig().isEmpty()) {
            card.add(Box.createVerticalStrut(6));
            String rewardsText = formatRewards(comp.getRewardConfig());
            JLabel rewardsLabel = new JLabel(rewardsText);
            rewardsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            rewardsLabel.setForeground(UIConstants.ACCENT_GOLD);
            rewardsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(rewardsLabel);
        }

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(UIConstants.CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(UIConstants.CARD_BG);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    showCompetitionDetails(comp);
                }
            }
        });

        return wrapInRoundedPanel(card);
    }

    private JPanel createLeaderboardPreview(List<CompetitionsResponse.LeaderboardEntry> leaderboard, String competitionType) {
        JPanel preview = new JPanel();
        preview.setLayout(new BoxLayout(preview, BoxLayout.Y_AXIS));
        preview.setOpaque(false);
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);

        String suffix = getValueSuffix(competitionType);
        int count = Math.min(3, leaderboard.size());
        for (int i = 0; i < count; i++) {
            CompetitionsResponse.LeaderboardEntry entry = leaderboard.get(i);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

            String medal = (i + 1) + ".";
            JLabel nameLabel = new JLabel(medal + " " + entry.getOsrsNickname());
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            nameLabel.setForeground(UIConstants.TEXT_PRIMARY);

            Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();
            JLabel valueLabel = new JLabel(value != null ? formatValue(value) + suffix : "-");
            valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            valueLabel.setForeground(UIConstants.ACCENT_BLUE);

            row.add(nameLabel, BorderLayout.WEST);
            row.add(valueLabel, BorderLayout.EAST);
            preview.add(row);
        }

        return preview;
    }

    private void showCompetitionDetails(CompetitionsResponse.Competition comp) {
        detailViewPanel.removeAll();

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UIConstants.CARD_BG);
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        BackButton backButton = new BackButton("← Back", this::showListView, true);

        JLabel titleLabel = new JLabel(comp.getName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(UIConstants.ACCENT_GOLD);

        header.add(backButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.EAST);
        detailViewPanel.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIConstants.BACKGROUND);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel loadingLabel = new JLabel("Loading leaderboard...");
        loadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        loadingLabel.setForeground(UIConstants.TEXT_SECONDARY);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(Box.createVerticalStrut(20));
        content.add(loadingLabel);

        JPanel contentWrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (getParent() != null) {
                    size.width = getParent().getWidth();
                }
                return size;
            }
        };
        contentWrapper.setBackground(UIConstants.BACKGROUND);
        contentWrapper.add(content, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setBackground(UIConstants.BACKGROUND);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(UIConstants.BACKGROUND);

        detailViewPanel.add(scrollPane, BorderLayout.CENTER);
        detailViewPanel.revalidate();
        detailViewPanel.repaint();

        cardLayout.show(cardContainer, DETAIL_VIEW);

        apiService.fetchCompetitionDetails(comp.getId(),
            response -> {
                SwingUtilities.invokeLater(() -> buildCompetitionDetailsContent(content, response.getData()));
            },
            error -> {
                SwingUtilities.invokeLater(() -> {
                    content.removeAll();
                    content.add(Box.createVerticalStrut(20));
                    JLabel errorLabel = new JLabel("Failed to load: " + error.getMessage());
                    errorLabel.setForeground(UIConstants.ERROR_COLOR);
                    errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    content.add(errorLabel);
                    content.revalidate();
                    content.repaint();
                });
            }
        );
    }

    private void buildCompetitionDetailsContent(JPanel content, CompetitionsResponse.Competition comp) {
        content.removeAll();

        if (comp == null) {
            JLabel errorLabel = new JLabel("Competition not found");
            errorLabel.setForeground(UIConstants.ERROR_COLOR);
            errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(errorLabel);
            content.revalidate();
            content.repaint();
            return;
        }

        if (comp.getDescription() != null) {
            JLabel descLabel = new JLabel("<html><body style='width: 220px'>" + comp.getDescription() + "</body></html>");
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descLabel.setForeground(UIConstants.TEXT_SECONDARY);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(descLabel);
            content.add(Box.createVerticalStrut(8));
        }

        // Status info
        boolean isActive = "active".equalsIgnoreCase(comp.getStatus());
        String timeText = isActive 
            ? "Ends: " + getTimeRemaining(comp.getEndDate())
            : "Starts: " + getTimeRemaining(comp.getStartDate());
        JLabel timeLabel = new JLabel(timeText);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(UIConstants.TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(timeLabel);

        if (comp.getParticipantCount() != null) {
            JLabel participantsLabel = new JLabel(comp.getParticipantCount() + " participants");
            participantsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            participantsLabel.setForeground(UIConstants.TEXT_MUTED);
            participantsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(participantsLabel);
        }

        content.add(Box.createVerticalStrut(12));

        // Leaderboard
        content.add(createSectionTitle("Leaderboard"));
        content.add(Box.createVerticalStrut(6));

        if (comp.getLeaderboard() != null && !comp.getLeaderboard().isEmpty()) {
            for (int i = 0; i < comp.getLeaderboard().size(); i++) {
                CompetitionsResponse.LeaderboardEntry entry = comp.getLeaderboard().get(i);
                content.add(createLeaderboardRow(entry, i + 1, comp.getCompetitionType()));
                content.add(Box.createVerticalStrut(2));
            }
        } else {
            JLabel noDataLabel = new JLabel("No participants yet");
            noDataLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            noDataLabel.setForeground(UIConstants.TEXT_MUTED);
            noDataLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(noDataLabel);
        }

        // Side quests
        if (comp.getSideQuests() != null && !comp.getSideQuests().isEmpty()) {
            content.add(Box.createVerticalStrut(16));
            content.add(createSectionTitle("Side Quests"));
            content.add(Box.createVerticalStrut(6));

            for (CompetitionsResponse.SideQuest sq : comp.getSideQuests()) {
                content.add(createSideQuestCard(sq));
                content.add(Box.createVerticalStrut(8));
            }
        }

        content.revalidate();
        content.repaint();
    }

    private JPanel createLeaderboardRow(CompetitionsResponse.LeaderboardEntry entry, int displayRank, String competitionType) {
        JPanel row = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2d.dispose();
            }
        };
        row.setOpaque(false);
        row.setBackground(UIConstants.CARD_BG);
        row.setBorder(new EmptyBorder(6, 10, 6, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        // Rank
        String rankText;
        Color rankColor;
        if (displayRank == 1) {
            rankText = "#1";
            rankColor = new Color(255, 215, 0);
        } else if (displayRank == 2) {
            rankText = "#2";
            rankColor = new Color(192, 192, 192);
        } else if (displayRank == 3) {
            rankText = "#3";
            rankColor = new Color(205, 127, 50);
        } else {
            rankText = "#" + displayRank;
            rankColor = UIConstants.TEXT_SECONDARY;
        }

        JLabel rankLabel = new JLabel(rankText);
        rankLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rankLabel.setForeground(rankColor);
        rankLabel.setPreferredSize(new Dimension(30, 20));

        // Name
        JLabel nameLabel = new JLabel(entry.getOsrsNickname());
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);

        // Value with suffix based on competition type
        Integer value = entry.getCurrentValue() != null ? entry.getCurrentValue() : entry.getFinalValue();
        String suffix = getValueSuffix(competitionType);
        JLabel valueLabel = new JLabel(value != null ? formatValue(value) + suffix : "-");
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        valueLabel.setForeground(UIConstants.ACCENT_GOLD);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(rankLabel);
        leftPanel.add(nameLabel);

        row.add(leftPanel, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    private JPanel createSideQuestCard(CompetitionsResponse.SideQuest sq) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(UIConstants.CARD_BG);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        // Title
        JLabel titleLabel = new JLabel(sq.getName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLabel.setForeground(UIConstants.ACCENT_PURPLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        // Description
        if (sq.getDescription() != null) {
            JLabel descLabel = new JLabel(sq.getDescription());
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            descLabel.setForeground(UIConstants.TEXT_SECONDARY);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(descLabel);
        }

        // Mini leaderboard (top 3)
        if (sq.getLeaderboard() != null && !sq.getLeaderboard().isEmpty()) {
            card.add(Box.createVerticalStrut(6));
            String suffix = getValueSuffix(sq.getSideQuestType());
            int count = Math.min(3, sq.getLeaderboard().size());
            for (int i = 0; i < count; i++) {
                CompetitionsResponse.LeaderboardEntry entry = sq.getLeaderboard().get(i);
                String medal = (i + 1) + ".";
                JLabel rowLabel = new JLabel(medal + " " + entry.getOsrsNickname() + " - " + formatValue(entry.getCurrentValue()) + suffix);
                rowLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                rowLabel.setForeground(UIConstants.TEXT_PRIMARY);
                rowLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(rowLabel);
            }
        }

        return wrapInRoundedPanel(card);
    }

    private void showListView() {
        cardLayout.show(cardContainer, LIST_VIEW);
    }

    private JPanel wrapInRoundedPanel(JPanel inner) {
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(inner.getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2d.dispose();
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(inner, BorderLayout.CENTER);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(inner.getMaximumSize());
        return wrapper;
    }

    private String getTimeRemaining(String dateStr) {
        if (dateStr == null) return "Unknown";
        try {
            ZonedDateTime target = ZonedDateTime.parse(dateStr);
            ZonedDateTime now = ZonedDateTime.now();
            
            long days = ChronoUnit.DAYS.between(now, target);
            long hours = ChronoUnit.HOURS.between(now, target) % 24;
            
            if (days < 0 || (days == 0 && hours < 0)) {
                return "Ended";
            } else if (days > 0) {
                return days + "d " + hours + "h";
            } else {
                long minutes = ChronoUnit.MINUTES.between(now, target) % 60;
                return hours + "h " + minutes + "m";
            }
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatRewards(java.util.Map<String, Integer> rewards) {
        StringBuilder sb = new StringBuilder();
        if (rewards.containsKey("1")) {
            sb.append("1st: ").append(rewards.get("1")).append(" pts");
        }
        if (rewards.containsKey("2")) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("2nd: ").append(rewards.get("2")).append(" pts");
        }
        if (rewards.containsKey("3")) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("3rd: ").append(rewards.get("3")).append(" pts");
        }
        return sb.toString();
    }

    private String formatValue(Integer value) {
        if (value == null) return "-";
        if (value >= 1000000) {
            return String.format("%.1fM", value / 1000000.0);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000.0);
        }
        return String.valueOf(value);
    }

    private String getValueSuffix(String competitionType) {
        if (competitionType == null) return "";
        String type = competitionType.toLowerCase();
        if (type.contains("xp") || type.contains("experience") || type.contains("skill")) {
            return " XP";
        } else if (type.contains("kc") || type.contains("kill") || type.contains("boss") || type.contains("npc")) {
            return " KC";
        }
        return "";
    }
}
