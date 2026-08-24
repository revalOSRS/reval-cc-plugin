package com.revalclan.api.events;

import com.revalclan.api.common.ApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** Response for GET /events/active-teams: rosters of active event teams. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActiveTeamsResponse extends ApiResponse {
	private TeamsData data;

	@Data
	public static class TeamsData {
		private List<Team> teams;
	}

	@Data
	public static class Team {
		private String eventId;
		private String eventName;
		private String name;
		private String color;   // hex, e.g. "#4DA6FF"; may be null
		private List<String> members;
	}
}
