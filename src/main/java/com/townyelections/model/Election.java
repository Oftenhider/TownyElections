package com.townyelections.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents the state of a single election within one town. Instances are
 * mutable and mutated only on the main server thread by the election manager.
 */
public class Election {

    private final UUID townUuid;
    private String townName;

    private ElectionPhase phase;
    private long phaseEndsAt;

    /** candidate uuid -> Candidate */
    private final Map<UUID, Candidate> candidates = new LinkedHashMap<>();

    /** voter uuid -> candidate uuid they voted for */
    private final Map<UUID, UUID> votes = new ConcurrentHashMap<>();

    /** Whether a reminder for the voting phase has already been dispatched. */
    private boolean reminderSent = false;

    public Election(UUID townUuid, String townName, ElectionPhase phase, long phaseEndsAt) {
        this.townUuid = townUuid;
        this.townName = townName;
        this.phase = phase;
        this.phaseEndsAt = phaseEndsAt;
    }

    // ---- Basic accessors ---------------------------------------------------

    public UUID getTownUuid() {
        return townUuid;
    }

    public String getTownName() {
        return townName;
    }

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public ElectionPhase getPhase() {
        return phase;
    }

    public void setPhase(ElectionPhase phase) {
        this.phase = phase;
    }

    public long getPhaseEndsAt() {
        return phaseEndsAt;
    }

    public void setPhaseEndsAt(long phaseEndsAt) {
        this.phaseEndsAt = phaseEndsAt;
    }

    public long getMillisRemaining() {
        return Math.max(0L, phaseEndsAt - System.currentTimeMillis());
    }

    public boolean isPhaseExpired() {
        return System.currentTimeMillis() >= phaseEndsAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    // ---- Candidates --------------------------------------------------------

    public Map<UUID, Candidate> getCandidates() {
        return candidates;
    }

    public List<Candidate> getCandidateList() {
        return new ArrayList<>(candidates.values());
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public boolean isCandidate(UUID uuid) {
        return candidates.containsKey(uuid);
    }

    public Candidate getCandidate(UUID uuid) {
        return candidates.get(uuid);
    }

    public void addCandidate(Candidate candidate) {
        candidates.put(candidate.getUuid(), candidate);
    }

    public void removeCandidate(UUID uuid) {
        candidates.remove(uuid);
        // Void any votes that were cast for the withdrawn candidate.
        votes.values().removeIf(uuid::equals);
    }

    /** Case-insensitive lookup of a candidate by resident name. */
    public Candidate findCandidateByName(String name) {
        for (Candidate candidate : candidates.values()) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    /** Restrict the candidate set to the provided uuids (used to begin a runoff). */
    public void retainCandidates(List<UUID> keep) {
        candidates.keySet().retainAll(keep);
        votes.clear();
        reminderSent = false;
    }

    // ---- Votes -------------------------------------------------------------

    public Map<UUID, UUID> getVotes() {
        return votes;
    }

    public boolean hasVoted(UUID voter) {
        return votes.containsKey(voter);
    }

    public UUID getVoteChoice(UUID voter) {
        return votes.get(voter);
    }

    public void castVote(UUID voter, UUID candidate) {
        votes.put(voter, candidate);
    }

    public int getTotalVotes() {
        return votes.size();
    }

    public int getUniqueVoterCount() {
        return votes.size();
    }

    /** candidate uuid -> tally, including candidates with zero votes. */
    public Map<UUID, Integer> tally() {
        Map<UUID, Integer> tally = new HashMap<>();
        for (UUID candidate : candidates.keySet()) {
            tally.put(candidate, 0);
        }
        for (UUID choice : votes.values()) {
            tally.merge(choice, 1, Integer::sum);
        }
        return tally;
    }

    /**
     * Returns candidate uuids ordered by descending vote count. Ties preserve
     * candidate insertion (registration) order via a stable sort.
     */
    public List<UUID> rankedCandidates() {
        Map<UUID, Integer> tally = tally();
        List<UUID> ordered = new ArrayList<>(candidates.keySet());
        ordered.sort(Comparator.comparingInt((UUID id) -> tally.getOrDefault(id, 0)).reversed());
        return ordered;
    }

    /**
     * Returns the uuids sharing the highest vote total (the potential winners).
     * A list larger than one indicates a tie.
     */
    public List<UUID> topCandidates() {
        Map<UUID, Integer> tally = tally();
        int max = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return tally.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ---- Serialization -----------------------------------------------------

    public void serialize(ConfigurationSection section) {
        section.set("town-uuid", townUuid.toString());
        section.set("town-name", townName);
        section.set("phase", phase.name());
        section.set("phase-ends-at", phaseEndsAt);
        section.set("reminder-sent", reminderSent);

        ConfigurationSection candidatesSection = section.createSection("candidates");
        int i = 0;
        for (Candidate candidate : candidates.values()) {
            candidate.serialize(candidatesSection.createSection("c" + (i++)));
        }

        ConfigurationSection votesSection = section.createSection("votes");
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            votesSection.set(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    public static Election deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String rawTown = section.getString("town-uuid");
        if (rawTown == null) {
            return null;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(rawTown);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String townName = section.getString("town-name", "Unknown");
        ElectionPhase phase;
        try {
            phase = ElectionPhase.valueOf(section.getString("phase", "NOMINATION"));
        } catch (IllegalArgumentException ex) {
            phase = ElectionPhase.NOMINATION;
        }
        long phaseEndsAt = section.getLong("phase-ends-at", System.currentTimeMillis());

        Election election = new Election(townUuid, townName, phase, phaseEndsAt);
        election.reminderSent = section.getBoolean("reminder-sent", false);

        ConfigurationSection candidatesSection = section.getConfigurationSection("candidates");
        if (candidatesSection != null) {
            for (String key : candidatesSection.getKeys(false)) {
                Candidate candidate = Candidate.deserialize(candidatesSection.getConfigurationSection(key));
                if (candidate != null) {
                    election.candidates.put(candidate.getUuid(), candidate);
                }
            }
        }

        ConfigurationSection votesSection = section.getConfigurationSection("votes");
        if (votesSection != null) {
            for (String key : votesSection.getKeys(false)) {
                try {
                    UUID voter = UUID.fromString(key);
                    UUID choice = UUID.fromString(votesSection.getString(key, ""));
                    // Only keep votes for candidates that still exist.
                    if (election.candidates.containsKey(choice)) {
                        election.votes.put(voter, choice);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed vote entries.
                }
            }
        }
        return election;
    }
}
