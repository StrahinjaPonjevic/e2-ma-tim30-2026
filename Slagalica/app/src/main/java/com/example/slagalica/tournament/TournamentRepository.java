package com.example.slagalica.tournament;

import com.example.slagalica.missions.MissionsRepository;
import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationStore;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.ranking.StarRewardHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentRepository {

    public static final int ENTRY_COST = 3;
    public static final String TYPE_TOURNAMENT = "tournament";

    private static final String TOURNAMENTS = "tournaments";
    private static final String USERS = "users";
    private static final String PARTIES = "parties";
    private static final String SESSIONS = "sessions";

    private final FirebaseFirestore db;

    public interface TournamentCallback {
        void onSuccess(String tournamentId);
        void onError(String message);
    }

    public interface FindCallback {
        void onFound(String tournamentId);
        void onNotFound();
        void onError(String message);
    }

    public interface TournamentListener {
        void onChanged(TournamentData tournament);
        void onError(String message);
    }

    public interface ProgressCallback {
        void onProgressed(List<String> newWinnerIds, String finalWinnerId);
        void onError(String message);
    }

    public TournamentRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void findMyActiveTournament(String uid, FindCallback callback) {
        db.collection(TOURNAMENTS)
                .whereArrayContains("playerIds", uid)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    TournamentData latest = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        TournamentData tournament = TournamentData.fromSnapshot(doc);
                        if (!tournament.isActive()) {
                            continue;
                        }
                        if (latest == null || isNewer(tournament, latest)) {
                            latest = tournament;
                        }
                    }
                    if (latest != null) {
                        callback.onFound(latest.tournamentId);
                    } else {
                        callback.onNotFound();
                    }
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Turnir nije dostupan")));
    }

    public void joinOpenTournament(String uid, String username, int stars, int avatarTheme,
                                   TournamentCallback callback) {
        db.collection(TOURNAMENTS)
                .whereEqualTo("status", TournamentData.STATUS_WAITING)
                .limit(5)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String openTournamentId = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        TournamentData tournament = TournamentData.fromSnapshot(doc);
                        if (!tournament.containsPlayer(uid) && tournament.playerIds.size() < 4) {
                            openTournamentId = tournament.tournamentId;
                            break;
                        }
                    }
                    if (openTournamentId != null) {
                        join(openTournamentId, uid, username, stars, avatarTheme, callback);
                    } else {
                        create(uid, username, stars, avatarTheme, callback);
                    }
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Turnir nije dostupan")));
    }

    private void create(String uid, String username, int stars, int avatarTheme, TournamentCallback callback) {
        DocumentReference tournamentRef = db.collection(TOURNAMENTS).document();
        DocumentReference userRef = db.collection(USERS).document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot userSnap = transaction.get(userRef);
                    validateJoiningUser(userSnap);

                    Map<String, Object> tournament = new HashMap<>();
                    tournament.put("status", TournamentData.STATUS_WAITING);
                    List<String> playerIds = new ArrayList<>();
                    playerIds.add(uid);
                    tournament.put("playerIds", playerIds);
                    List<Map<String, Object>> players = new ArrayList<>();
                    players.add(playerMap(uid, username, stars, avatarTheme));
                    tournament.put("players", players);
                    tournament.put("semi1PartyId", null);
                    tournament.put("semi2PartyId", null);
                    tournament.put("finalPartyId", null);
                    tournament.put("semi1WinnerId", null);
                    tournament.put("semi2WinnerId", null);
                    tournament.put("semi1Rewarded", false);
                    tournament.put("semi2Rewarded", false);
                    tournament.put("finalWinnerId", null);
                    tournament.put("finalRewarded", false);
                    tournament.put("createdAt", FieldValue.serverTimestamp());
                    tournament.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.set(tournamentRef, tournament);
                    transaction.update(userRef,
                            "tokens", FieldValue.increment(-ENTRY_COST),
                            "updatedAt", FieldValue.serverTimestamp());
                    return tournamentRef.getId();
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Turnir nije kreiran")));
    }

    private void join(String tournamentId, String uid, String username, int stars, int avatarTheme,
                      TournamentCallback callback) {
        DocumentReference tournamentRef = db.collection(TOURNAMENTS).document(tournamentId);
        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference semi1PartyRef = db.collection(PARTIES).document();
        DocumentReference semi2PartyRef = db.collection(PARTIES).document();

        db.runTransaction(transaction -> {
                    DocumentSnapshot tournamentSnap = transaction.get(tournamentRef);
                    if (!tournamentSnap.exists()) {
                        throw abort("Turnir vise ne postoji");
                    }
                    TournamentData tournament = TournamentData.fromSnapshot(tournamentSnap);
                    if (!TournamentData.STATUS_WAITING.equals(tournament.status)
                            || tournament.playerIds.size() >= 4) {
                        throw abort("Turnir je vec popunjen");
                    }
                    if (tournament.containsPlayer(uid)) {
                        throw abort("Vec ste prijavljeni na ovaj turnir");
                    }

                    DocumentSnapshot userSnap = transaction.get(userRef);
                    validateJoiningUser(userSnap);

                    List<String> playerIds = new ArrayList<>(tournament.playerIds);
                    playerIds.add(uid);
                    List<Map<String, Object>> players = new ArrayList<>(tournament.players);
                    players.add(playerMap(uid, username, stars, avatarTheme));

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("playerIds", playerIds);
                    updates.put("players", players);
                    updates.put("updatedAt", FieldValue.serverTimestamp());

                    if (playerIds.size() == 4) {
                        Map<String, Object> p1 = players.get(0);
                        Map<String, Object> p2 = players.get(1);
                        Map<String, Object> p3 = players.get(2);
                        Map<String, Object> p4 = players.get(3);

                        transaction.set(semi1PartyRef, buildTournamentPartyMap(p1, p2));
                        transaction.set(db.collection(SESSIONS).document(semi1PartyRef.getId()),
                                buildTournamentSessionMap(p1, p2));
                        transaction.set(semi2PartyRef, buildTournamentPartyMap(p3, p4));
                        transaction.set(db.collection(SESSIONS).document(semi2PartyRef.getId()),
                                buildTournamentSessionMap(p3, p4));

                        setActiveParty(transaction, (String) p1.get("uid"), semi1PartyRef.getId());
                        setActiveParty(transaction, (String) p2.get("uid"), semi1PartyRef.getId());
                        setActiveParty(transaction, (String) p3.get("uid"), semi2PartyRef.getId());
                        transaction.update(userRef,
                                "tokens", FieldValue.increment(-ENTRY_COST),
                                "activePartyId", semi2PartyRef.getId(),
                                "updatedAt", FieldValue.serverTimestamp());

                        updates.put("status", TournamentData.STATUS_SEMIFINALS);
                        updates.put("semi1PartyId", semi1PartyRef.getId());
                        updates.put("semi2PartyId", semi2PartyRef.getId());
                    } else {
                        transaction.update(userRef,
                                "tokens", FieldValue.increment(-ENTRY_COST),
                                "updatedAt", FieldValue.serverTimestamp());
                    }

                    transaction.update(tournamentRef, updates);
                    return tournamentId;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Prijava na turnir nije uspela")));
    }

    public void leaveWhileWaiting(String tournamentId, String uid, TournamentCallback callback) {
        DocumentReference tournamentRef = db.collection(TOURNAMENTS).document(tournamentId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot tournamentSnap = transaction.get(tournamentRef);
                    if (!tournamentSnap.exists()) {
                        return tournamentId;
                    }
                    TournamentData tournament = TournamentData.fromSnapshot(tournamentSnap);
                    if (!TournamentData.STATUS_WAITING.equals(tournament.status)
                            || !tournament.containsPlayer(uid)) {
                        throw abort("Turnir je vec poceo, ne mozete izaci");
                    }

                    List<String> playerIds = new ArrayList<>(tournament.playerIds);
                    playerIds.remove(uid);
                    List<Map<String, Object>> players = new ArrayList<>();
                    for (Map<String, Object> player : tournament.players) {
                        if (!uid.equals(player.get("uid"))) {
                            players.add(player);
                        }
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("playerIds", playerIds);
                    updates.put("players", players);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(tournamentRef, updates);
                    return tournamentId;
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Izlazak nije uspeo")));
    }

    public ListenerRegistration listenTournament(String tournamentId, TournamentListener listener) {
        return db.collection(TOURNAMENTS).document(tournamentId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri osluskivanju turnira"));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onError("Turnir nije pronadjen");
                        return;
                    }
                    listener.onChanged(TournamentData.fromSnapshot(snapshot));
                });
    }

    public void recordProgressIfNeeded(String tournamentId, ProgressCallback callback) {
        DocumentReference tournamentRef = db.collection(TOURNAMENTS).document(tournamentId);
        DocumentReference finalPartyRef = db.collection(PARTIES).document();

        db.runTransaction(transaction -> {
                    DocumentSnapshot tournamentSnap = transaction.get(tournamentRef);
                    if (!tournamentSnap.exists()) {
                        return null;
                    }
                    TournamentData tournament = TournamentData.fromSnapshot(tournamentSnap);

                    if (TournamentData.STATUS_SEMIFINALS.equals(tournament.status)) {
                        return progressSemifinals(transaction, tournamentRef, finalPartyRef, tournament);
                    }
                    if (TournamentData.STATUS_FINAL.equals(tournament.status) && !tournament.finalRewarded) {
                        return progressFinal(transaction, tournamentRef, tournament);
                    }
                    return null;
                })
                .addOnSuccessListener(result -> {
                    if (result == null) {
                        if (callback != null) callback.onProgressed(new ArrayList<>(), null);
                        return;
                    }
                    for (String winnerId : result.newWinners) {
                        MissionsRepository.markTournamentWin(winnerId);
                    }
                    if (result.finalWinnerId != null) {
                        NotificationStore.save(result.finalWinnerId,
                                NotificationChannelManager.CHANNEL_REWARDS,
                                "Pobeda na turniru",
                                "Osvojili ste turnir! Nagrada: +3 tokena i +10 dodatnih zvezda.",
                                "reward",
                                tournamentId);
                    }
                    if (callback != null) callback.onProgressed(result.newWinners, result.finalWinnerId);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri azuriranju turnira"));
                });
    }

    private ProgressResult progressSemifinals(com.google.firebase.firestore.Transaction transaction,
                                              DocumentReference tournamentRef,
                                              DocumentReference finalPartyRef,
                                              TournamentData tournament) throws FirebaseFirestoreException {
        DocumentSnapshot semi1Snap = tournament.semi1PartyId != null
                ? transaction.get(db.collection(PARTIES).document(tournament.semi1PartyId)) : null;
        DocumentSnapshot semi2Snap = tournament.semi2PartyId != null
                ? transaction.get(db.collection(PARTIES).document(tournament.semi2PartyId)) : null;

        PartyOutcome outcome1 = resolveOutcome(semi1Snap);
        PartyOutcome outcome2 = resolveOutcome(semi2Snap);

        String winner1 = tournament.semi1WinnerId != null ? tournament.semi1WinnerId
                : (outcome1 != null ? outcome1.winnerUid : null);
        String winner2 = tournament.semi2WinnerId != null ? tournament.semi2WinnerId
                : (outcome2 != null ? outcome2.winnerUid : null);

        boolean rewardSemi1 = !tournament.semi1Rewarded && tournament.semi1WinnerId == null && outcome1 != null;
        boolean rewardSemi2 = !tournament.semi2Rewarded && tournament.semi2WinnerId == null && outcome2 != null;
        boolean createFinal = winner1 != null && winner2 != null && tournament.finalPartyId == null;

        if (!rewardSemi1 && !rewardSemi2 && !createFinal) {
            return null;
        }

        DocumentSnapshot winner1Snap = rewardSemi1 || createFinal
                ? transaction.get(db.collection(USERS).document(winner1)) : null;
        DocumentSnapshot winner2Snap = rewardSemi2 || createFinal
                ? transaction.get(db.collection(USERS).document(winner2)) : null;

        ProgressResult result = new ProgressResult();
        Map<String, Object> updates = new HashMap<>();

        if (rewardSemi1) {
            applySemifinalReward(transaction, winner1, winner1Snap, outcome1.winnerScore);
            updates.put("semi1WinnerId", winner1);
            updates.put("semi1Rewarded", true);
            result.newWinners.add(winner1);
        }
        if (rewardSemi2) {
            applySemifinalReward(transaction, winner2, winner2Snap, outcome2.winnerScore);
            updates.put("semi2WinnerId", winner2);
            updates.put("semi2Rewarded", true);
            result.newWinners.add(winner2);
        }

        if (createFinal) {
            Map<String, Object> finalist1 = playerInfoOrFallback(tournament, winner1, winner1Snap);
            Map<String, Object> finalist2 = playerInfoOrFallback(tournament, winner2, winner2Snap);
            transaction.set(finalPartyRef, buildTournamentPartyMap(finalist1, finalist2));
            transaction.set(db.collection(SESSIONS).document(finalPartyRef.getId()),
                    buildTournamentSessionMap(finalist1, finalist2));
            transaction.update(db.collection(USERS).document(winner1),
                    "activePartyId", finalPartyRef.getId(),
                    "updatedAt", FieldValue.serverTimestamp());
            transaction.update(db.collection(USERS).document(winner2),
                    "activePartyId", finalPartyRef.getId(),
                    "updatedAt", FieldValue.serverTimestamp());
            updates.put("finalPartyId", finalPartyRef.getId());
            updates.put("status", TournamentData.STATUS_FINAL);
        }

        updates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.update(tournamentRef, updates);
        return result;
    }

    private ProgressResult progressFinal(com.google.firebase.firestore.Transaction transaction,
                                         DocumentReference tournamentRef,
                                         TournamentData tournament) throws FirebaseFirestoreException {
        if (tournament.finalPartyId == null) {
            return null;
        }
        DocumentSnapshot finalSnap = transaction.get(db.collection(PARTIES).document(tournament.finalPartyId));
        PartyOutcome outcome = resolveOutcome(finalSnap);
        if (outcome == null) {
            return null;
        }

        DocumentSnapshot winnerSnap = transaction.get(db.collection(USERS).document(outcome.winnerUid));
        DocumentSnapshot loserSnap = outcome.loserUid != null
                ? transaction.get(db.collection(USERS).document(outcome.loserUid)) : null;

        if (winnerSnap.exists()) {
            int winnerStars = 10 + outcome.winnerScore / 40 + 10;
            transaction.update(db.collection(USERS).document(outcome.winnerUid),
                    StarRewardHelper.starAwardUpdates(winnerSnap, winnerStars, 3));
        }
        if (loserSnap != null && loserSnap.exists()) {
            int loserStars = -10 + outcome.loserScore / 40;
            transaction.update(db.collection(USERS).document(outcome.loserUid),
                    StarRewardHelper.starAwardUpdates(loserSnap, loserStars, 0));
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("finalWinnerId", outcome.winnerUid);
        updates.put("finalRewarded", true);
        updates.put("status", TournamentData.STATUS_FINISHED);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        transaction.update(tournamentRef, updates);

        ProgressResult result = new ProgressResult();
        result.newWinners.add(outcome.winnerUid);
        result.finalWinnerId = outcome.winnerUid;
        return result;
    }

    private void applySemifinalReward(com.google.firebase.firestore.Transaction transaction,
                                      String winnerUid, DocumentSnapshot winnerSnap, int winnerScore) {
        if (winnerSnap == null || !winnerSnap.exists()) {
            return;
        }
        int starsDelta = 10 + winnerScore / 40;
        transaction.update(db.collection(USERS).document(winnerUid),
                StarRewardHelper.starAwardUpdates(winnerSnap, starsDelta, 2));
    }

    private Map<String, Object> playerInfoOrFallback(TournamentData tournament, String uid,
                                                     DocumentSnapshot userSnap) {
        Map<String, Object> info = tournament.playerInfo(uid);
        if (info != null) {
            return info;
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("uid", uid);
        fallback.put("username", userSnap != null && userSnap.exists()
                ? valueOrDefault(userSnap.getString("username"), "Igrac") : "Igrac");
        fallback.put("stars", 0);
        fallback.put("avatarTheme", 0);
        return fallback;
    }

    private PartyOutcome resolveOutcome(DocumentSnapshot partySnap) {
        if (partySnap == null || !partySnap.exists()) {
            return null;
        }
        PartyData party = PartyData.fromSnapshot(partySnap);
        boolean done = PartyData.STATUS_FINISHED.equals(party.status)
                || PartyData.STATUS_FORFEITED.equals(party.status);
        if (!done) {
            return null;
        }

        String winnerUid = party.winner;
        if (winnerUid == null || "draw".equals(winnerUid)) {
            if (party.forfeitedBy != null) {
                winnerUid = party.forfeitedBy.equals(party.ownerId) ? party.guestId : party.ownerId;
            } else {
                winnerUid = party.ownerId;
            }
        }

        PartyOutcome outcome = new PartyOutcome();
        outcome.winnerUid = winnerUid;
        outcome.loserUid = winnerUid.equals(party.ownerId) ? party.guestId : party.ownerId;
        outcome.winnerScore = winnerUid.equals(party.ownerId) ? party.ownerTotalScore : party.guestTotalScore;
        outcome.loserScore = winnerUid.equals(party.ownerId) ? party.guestTotalScore : party.ownerTotalScore;
        return outcome;
    }

    private Map<String, Object> buildTournamentPartyMap(Map<String, Object> owner, Map<String, Object> guest) {
        Map<String, Object> party = new HashMap<>();
        party.put("ownerId", owner.get("uid"));
        party.put("ownerUsername", valueOrDefault((String) owner.get("username"), "Igrac 1"));
        party.put("guestId", guest.get("uid"));
        party.put("guestUsername", valueOrDefault((String) guest.get("username"), "Igrac 2"));
        party.put("type", TYPE_TOURNAMENT);
        party.put("status", PartyData.STATUS_IN_PROGRESS);
        party.put("currentGameIndex", 0);
        party.put("currentGameKey", PartyData.GAME_KEYS[0]);
        party.put("ownerTotalScore", 0);
        party.put("guestTotalScore", 0);
        party.put("gameScores", new HashMap<String, Object>());
        party.put("winner", null);
        party.put("forfeitedBy", null);
        party.put("ownerForfeited", false);
        party.put("guestForfeited", false);
        party.put("countsForStats", true);
        party.put("usesTokens", false);
        party.put("rewardApplied", false);
        party.put("createdAt", FieldValue.serverTimestamp());
        party.put("updatedAt", FieldValue.serverTimestamp());
        return party;
    }

    private Map<String, Object> buildTournamentSessionMap(Map<String, Object> owner, Map<String, Object> guest) {
        Map<String, Object> session = new HashMap<>();
        session.put("ownerId", owner.get("uid"));
        session.put("ownerUsername", valueOrDefault((String) owner.get("username"), "Igrac 1"));
        session.put("guestId", guest.get("uid"));
        session.put("guestUsername", valueOrDefault((String) guest.get("username"), "Igrac 2"));
        session.put("status", "joined");
        session.put("code", "");
        session.put("selectedGame", "");
        session.put("createdAt", FieldValue.serverTimestamp());
        session.put("updatedAt", FieldValue.serverTimestamp());
        return session;
    }

    private void setActiveParty(com.google.firebase.firestore.Transaction transaction, String uid, String partyId) {
        transaction.update(db.collection(USERS).document(uid),
                "activePartyId", partyId,
                "updatedAt", FieldValue.serverTimestamp());
    }

    private void validateJoiningUser(DocumentSnapshot userSnap) throws FirebaseFirestoreException {
        if (userSnap == null || !userSnap.exists()) {
            throw abort("Profil nije pronadjen");
        }
        if (intValue(userSnap.get("tokens")) < ENTRY_COST) {
            throw abort("Potrebna su vam " + ENTRY_COST + " tokena za turnir");
        }
        String activePartyId = userSnap.getString("activePartyId");
        if (activePartyId != null && !activePartyId.trim().isEmpty()) {
            throw abort("Vec ucestvujete u partiji");
        }
    }

    private Map<String, Object> playerMap(String uid, String username, int stars, int avatarTheme) {
        Map<String, Object> player = new HashMap<>();
        player.put("uid", uid);
        player.put("username", valueOrDefault(username, "Igrac"));
        player.put("stars", stars);
        player.put("avatarTheme", avatarTheme);
        return player;
    }

    private boolean isNewer(TournamentData left, TournamentData right) {
        long leftMs = left.createdAt != null ? left.createdAt.toDate().getTime() : 0L;
        long rightMs = right.createdAt != null ? right.createdAt.toDate().getTime() : 0L;
        return leftMs >= rightMs;
    }

    private FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.ABORTED);
    }

    private String messageOf(Exception e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : fallback;
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private static final class ProgressResult {
        final List<String> newWinners = new ArrayList<>();
        String finalWinnerId;
    }

    private static final class PartyOutcome {
        String winnerUid;
        String loserUid;
        int winnerScore;
        int loserScore;
    }
}
