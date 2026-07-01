package com.example.slagalica.party;

import com.example.slagalica.leagues.LeagueProgressionHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PartyRepository {

    private static final String PARTIES = "parties";
    private static final String QUEUE = "matchmaking_queue";
    private static final String USERS = "users";
    private static final String SESSIONS = "sessions";
    private static final int REGULAR_TOKEN_COST = 1;

    private final FirebaseFirestore db;

    public interface MatchmakingCallback {
        void onPartyReady(String partyId);
        void onWaiting();
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface CreatePartyCallback {
        void onSuccess(String partyId);
        void onError(String message);
    }

    public interface PartyListener {
        void onPartyChanged(PartyData party);
        void onError(String message);
    }

    public PartyRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void findRandomOpponentOrWait(String uid, String username, MatchmakingCallback callback) {
        db.collection(QUEUE)
                .whereEqualTo("status", "waiting")
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    QueryDocumentSnapshot opponent = null;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (!uid.equals(doc.getId())) {
                            opponent = doc;
                            break;
                        }
                    }

                    if (opponent == null) {
                        addCurrentUserToQueue(uid, username, callback);
                        return;
                    }

                    String opponentUid = opponent.getId();
                    String opponentUsername = valueOrDefault(opponent.getString("username"), "Igrac");
                    createRegularPartyFromQueue(opponentUid, opponentUsername, uid, username, callback);
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Greska pri trazenju protivnika")));
    }

    public void cancelQueue(String uid, OperationCallback callback) {
        db.collection(QUEUE).document(uid)
                .delete()
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri izlasku iz reda cekanja"));
                });
    }

    public void createFriendlyParty(String inviterId, String inviterUsername, String friendId,
                                    String friendUsername, CreatePartyCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document();
        DocumentReference inviterRef = db.collection(USERS).document(inviterId);
        DocumentReference friendRef = db.collection(USERS).document(friendId);
        DocumentReference sessionRef = db.collection(SESSIONS).document(partyRef.getId());
        Map<String, Object> party = buildPartyMap(
                inviterId,
                inviterUsername,
                friendId,
                friendUsername,
                PartyData.TYPE_FRIENDLY,
                false,
                false
        );

        Map<String, Object> session = buildCompatSessionMap(inviterId, inviterUsername, friendId, friendUsername);
        db.runTransaction(transaction -> {
                    DocumentSnapshot inviter = transaction.get(inviterRef);
                    DocumentSnapshot friend = transaction.get(friendRef);
                    if (!inviter.exists() || !friend.exists()) {
                        throw abort("Korisnik nije pronadjen");
                    }
                    if (hasActiveParty(inviter) || hasActiveParty(friend)) {
                        throw abort("Jedan od igraca je vec u partiji");
                    }
                    transaction.set(partyRef, party);
                    transaction.set(sessionRef, session);
                    transaction.update(inviterRef,
                            "activePartyId", partyRef.getId(),
                            "updatedAt", FieldValue.serverTimestamp());
                    transaction.update(friendRef,
                            "activePartyId", partyRef.getId(),
                            "updatedAt", FieldValue.serverTimestamp());
                    return partyRef.getId();
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Greska pri kreiranju prijateljske partije")));
    }

    public ListenerRegistration listenParty(String partyId, PartyListener listener) {
        return db.collection(PARTIES).document(partyId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri osluškivanju partije"));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onError("Partija nije pronadjena");
                        return;
                    }
                    listener.onPartyChanged(PartyData.fromSnapshot(snapshot));
                });
    }

    public ListenerRegistration listenOwnedInProgressParty(String ownerId, long minCreatedAtMs, PartyListener listener) {
        return db.collection(PARTIES)
                .whereEqualTo("ownerId", ownerId)
                .limit(5)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri cekanju protivnika"));
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        PartyData party = PartyData.fromSnapshot(doc);
                        if (PartyData.STATUS_IN_PROGRESS.equals(party.status)
                                && isRecentEnoughForQueueMatch(party, minCreatedAtMs)) {
                            listener.onPartyChanged(party);
                            return;
                        }
                    }
                });
    }

    private boolean isRecentEnoughForQueueMatch(PartyData party, long minCreatedAtMs) {
        if (minCreatedAtMs <= 0L) {
            return true;
        }

        long createdAtMs = party.createdAt != null ? party.createdAt.toDate().getTime() : 0L;
        long updatedAtMs = party.updatedAt != null ? party.updatedAt.toDate().getTime() : 0L;
        long toleranceMs = 5_000L;
        return createdAtMs >= (minCreatedAtMs - toleranceMs)
                || updatedAtMs >= (minCreatedAtMs - toleranceMs);
    }

    public void finishGameAndAdvance(String partyId, String gameKey, int ownerScore, int guestScore,
                                     OperationCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document(partyId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot partySnap = transaction.get(partyRef);
                    if (!partySnap.exists()) {
                        throw abort("Partija nije pronadjena");
                    }

                    PartyData party = PartyData.fromSnapshot(partySnap);
                    if (!canAdvanceGame(party, gameKey)) {
                        return null;
                    }

                    boolean finalGame = party.currentGameIndex >= PartyData.GAME_KEYS.length - 1;
                    DocumentSnapshot ownerUser = null;
                    DocumentSnapshot guestUser = null;
                    DocumentReference ownerRef = db.collection(USERS).document(party.ownerId);
                    DocumentReference guestRef = db.collection(USERS).document(party.guestId);
                    boolean applyRewards = shouldApplyRewards(party, finalGame);
                    if (applyRewards) {
                        ownerUser = transaction.get(ownerRef);
                        guestUser = transaction.get(guestRef);
                    }

                    int effectiveOwnerScore = normalizedOwnerScore(party, ownerScore, guestScore);
                    int effectiveGuestScore = normalizedGuestScore(party, ownerScore, guestScore);
                    int newOwnerTotal = party.ownerTotalScore + effectiveOwnerScore;
                    int newGuestTotal = party.guestTotalScore + effectiveGuestScore;
                    Map<String, Object> updates = buildAdvanceUpdates(party, gameKey, effectiveOwnerScore, effectiveGuestScore,
                            newOwnerTotal, newGuestTotal, finalGame);
                    transaction.update(partyRef, updates);

                    if (applyRewards) {
                        applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                newOwnerTotal, newGuestTotal, party.forfeitedBy, true);
                    } else if (finalGame) {
                        clearActiveParty(transaction, ownerRef, guestRef);
                    }
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri zavrsetku igre"));
                });
    }

    public void submitPlayerGameScoreAndAdvance(String partyId, String gameKey, String uid, int score,
                                                OperationCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document(partyId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot partySnap = transaction.get(partyRef);
                    if (!partySnap.exists()) {
                        throw abort("Partija nije pronadjena");
                    }

                    PartyData party = PartyData.fromSnapshot(partySnap);
                    if (!canAdvanceGame(party, gameKey)) {
                        return null;
                    }

                    boolean ownerSide = uid != null && uid.equals(party.ownerId);
                    boolean guestSide = uid != null && uid.equals(party.guestId);
                    if (!ownerSide && !guestSide) {
                        throw abort("Korisnik nije u partiji");
                    }

                    Map<String, Object> gameScore = party.currentGameScoreMap();
                    String ownField = ownerSide ? "ownerScore" : "guestScore";
                    String otherField = ownerSide ? "guestScore" : "ownerScore";
                    if (gameScore.get(ownField) instanceof Number) {
                        return null;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("gameScores." + gameKey + "." + ownField, score);
                    updates.put("gameScores." + gameKey + "." + (ownerSide ? "ownerSubmittedAt" : "guestSubmittedAt"),
                            FieldValue.serverTimestamp());
                    updates.put("updatedAt", FieldValue.serverTimestamp());

                    Object rawOtherScore = gameScore.get(otherField);
                    boolean otherPlayerForfeited = (ownerSide && party.guestForfeited)
                            || (guestSide && party.ownerForfeited);
                    if (!(rawOtherScore instanceof Number) && otherPlayerForfeited) {
                        rawOtherScore = 0;
                        updates.put("gameScores." + gameKey + "." + otherField, 0);
                        updates.put("gameScores." + gameKey + "." + (ownerSide ? "guestSubmittedAt" : "ownerSubmittedAt"),
                                FieldValue.serverTimestamp());
                    }
                    if (rawOtherScore instanceof Number) {
                        int ownerScore = ownerSide ? score : ((Number) rawOtherScore).intValue();
                        int guestScore = ownerSide ? ((Number) rawOtherScore).intValue() : score;
                        boolean finalGame = party.currentGameIndex >= PartyData.GAME_KEYS.length - 1;
                        DocumentSnapshot ownerUser = null;
                        DocumentSnapshot guestUser = null;
                        DocumentReference ownerRef = db.collection(USERS).document(party.ownerId);
                        DocumentReference guestRef = db.collection(USERS).document(party.guestId);
                        boolean applyRewards = shouldApplyRewards(party, finalGame);
                        if (applyRewards) {
                            ownerUser = transaction.get(ownerRef);
                            guestUser = transaction.get(guestRef);
                        }

                        int effectiveOwnerScore = normalizedOwnerScore(party, ownerScore, guestScore);
                        int effectiveGuestScore = normalizedGuestScore(party, ownerScore, guestScore);
                        int newOwnerTotal = party.ownerTotalScore + effectiveOwnerScore;
                        int newGuestTotal = party.guestTotalScore + effectiveGuestScore;
                        updates.putAll(buildAdvanceUpdates(party, gameKey, effectiveOwnerScore, effectiveGuestScore,
                                newOwnerTotal, newGuestTotal, finalGame));

                        if (applyRewards) {
                            applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                    newOwnerTotal, newGuestTotal, party.forfeitedBy, true);
                        } else if (finalGame) {
                            clearActiveParty(transaction, ownerRef, guestRef);
                        }
                    }

                    transaction.update(partyRef, updates);
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri predaji rezultata"));
                });
    }

    public void forfeitParty(String partyId, String forfeitedBy, OperationCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document(partyId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot partySnap = transaction.get(partyRef);
                    if (!partySnap.exists()) {
                        throw abort("Partija nije pronadjena");
                    }

                    PartyData party = PartyData.fromSnapshot(partySnap);
                    if (PartyData.STATUS_FINISHED.equals(party.status)
                            || PartyData.STATUS_FORFEITED.equals(party.status)) {
                        return null;
                    }

                    boolean ownerForfeited = forfeitedBy != null && forfeitedBy.equals(party.ownerId);
                    boolean guestForfeited = forfeitedBy != null && forfeitedBy.equals(party.guestId);
                    if (!ownerForfeited && !guestForfeited) {
                        throw abort("Korisnik nije u partiji");
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("forfeitedBy", forfeitedBy);
                    updates.put(ownerForfeited ? "ownerForfeited" : "guestForfeited", true);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    party.ownerForfeited = ownerForfeited || party.ownerForfeited;
                    party.guestForfeited = guestForfeited || party.guestForfeited;

                    Map<String, Object> gameScore = party.currentGameScoreMap();
                    String forfeitedScoreField = ownerForfeited ? "ownerScore" : "guestScore";
                    String otherScoreField = ownerForfeited ? "guestScore" : "ownerScore";
                    if (!(gameScore.get(forfeitedScoreField) instanceof Number)) {
                        updates.put("gameScores." + party.currentGameKey + "." + forfeitedScoreField, 0);
                        updates.put("gameScores." + party.currentGameKey + "." + (ownerForfeited ? "ownerSubmittedAt" : "guestSubmittedAt"),
                                FieldValue.serverTimestamp());
                    }

                    Object rawOtherScore = gameScore.get(otherScoreField);
                    if (rawOtherScore instanceof Number) {
                        int otherScore = ((Number) rawOtherScore).intValue();
                        int ownerScore = ownerForfeited ? 0 : otherScore;
                        int guestScore = ownerForfeited ? otherScore : 0;
                        boolean finalGame = party.currentGameIndex >= PartyData.GAME_KEYS.length - 1;
                        DocumentSnapshot ownerUser = null;
                        DocumentSnapshot guestUser = null;
                        DocumentReference ownerRef = db.collection(USERS).document(party.ownerId);
                        DocumentReference guestRef = db.collection(USERS).document(party.guestId);
                        boolean applyRewards = shouldApplyRewards(party, finalGame);
                        if (applyRewards) {
                            ownerUser = transaction.get(ownerRef);
                            guestUser = transaction.get(guestRef);
                        }

                        int effectiveOwnerScore = normalizedOwnerScore(party, ownerScore, guestScore);
                        int effectiveGuestScore = normalizedGuestScore(party, ownerScore, guestScore);
                        int newOwnerTotal = party.ownerTotalScore + effectiveOwnerScore;
                        int newGuestTotal = party.guestTotalScore + effectiveGuestScore;
                        updates.putAll(buildAdvanceUpdates(party, party.currentGameKey, effectiveOwnerScore, effectiveGuestScore,
                                newOwnerTotal, newGuestTotal, finalGame));

                        if (applyRewards) {
                            applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                    newOwnerTotal, newGuestTotal, forfeitedBy, true);
                        } else if (finalGame) {
                            clearActiveParty(transaction, ownerRef, guestRef);
                        }
                    }

                    transaction.update(partyRef, updates);
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri odustajanju"));
                });
    }

    public void cleanupInactiveForfeitedParty(String partyId, long staleAfterMs, OperationCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document(partyId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot partySnap = transaction.get(partyRef);
                    if (!partySnap.exists()) {
                        return null;
                    }

                    PartyData party = PartyData.fromSnapshot(partySnap);
                    if (!PartyData.STATUS_IN_PROGRESS.equals(party.status) || !party.hasForfeit()) {
                        return null;
                    }

                    long referenceMs = 0L;
                    if (party.updatedAt != null) {
                        referenceMs = party.updatedAt.toDate().getTime();
                    } else if (party.createdAt != null) {
                        referenceMs = party.createdAt.toDate().getTime();
                    }
                    if (referenceMs > 0L && System.currentTimeMillis() - referenceMs < staleAfterMs) {
                        return null;
                    }

                    Map<String, Object> gameScore = party.currentGameScoreMap();
                    int rawOwnerScore = intValue(gameScore.get("ownerScore"));
                    int rawGuestScore = intValue(gameScore.get("guestScore"));
                    int effectiveOwnerScore = normalizedOwnerScore(party, rawOwnerScore, rawGuestScore);
                    int effectiveGuestScore = normalizedGuestScore(party, rawOwnerScore, rawGuestScore);
                    int newOwnerTotal = party.ownerTotalScore + effectiveOwnerScore;
                    int newGuestTotal = party.guestTotalScore + effectiveGuestScore;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("gameScores." + party.currentGameKey + ".ownerScore", effectiveOwnerScore);
                    updates.put("gameScores." + party.currentGameKey + ".guestScore", effectiveGuestScore);
                    updates.put("gameScores." + party.currentGameKey + ".winner",
                            determineSideWinner(effectiveOwnerScore, effectiveGuestScore));
                    updates.put("gameScores." + party.currentGameKey + ".finishedAt", FieldValue.serverTimestamp());
                    updates.put("ownerTotalScore", newOwnerTotal);
                    updates.put("guestTotalScore", newGuestTotal);
                    updates.put("status", PartyData.STATUS_FINISHED);
                    updates.put("winner", determinePartyWinnerId(party, newOwnerTotal, newGuestTotal, party.forfeitedBy));
                    updates.put("rewardApplied", party.isRegular() && party.countsForStats);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    DocumentReference ownerRef = db.collection(USERS).document(party.ownerId);
                    DocumentReference guestRef = db.collection(USERS).document(party.guestId);
                    DocumentSnapshot ownerUser = null;
                    DocumentSnapshot guestUser = null;
                    boolean applyRewards = party.isRegular() && party.countsForStats && !party.rewardApplied;
                    if (applyRewards) {
                        ownerUser = transaction.get(ownerRef);
                        guestUser = transaction.get(guestRef);
                    }

                    transaction.update(partyRef, updates);

                    if (applyRewards) {
                        applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                newOwnerTotal, newGuestTotal, party.forfeitedBy, true);
                    } else {
                        clearActiveParty(transaction, ownerRef, guestRef);
                    }

                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska pri automatskom zatvaranju partije"));
                });
    }

    private void addCurrentUserToQueue(String uid, String username, MatchmakingCallback callback) {
        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(userSnap -> {
                    if (hasActiveParty(userSnap)) {
                        callback.onError("Vec ucestvujete u partiji.");
                        return;
                    }
                    if (intValue(userSnap.get("tokens")) < REGULAR_TOKEN_COST) {
                        callback.onError("Nemate dovoljno tokena za regularnu partiju.");
                        return;
                    }

                    Map<String, Object> queueData = new HashMap<>();
                    queueData.put("uid", uid);
                    queueData.put("username", valueOrDefault(username, "Igrac"));
                    queueData.put("status", "waiting");
                    queueData.put("createdAt", FieldValue.serverTimestamp());

                    db.collection(QUEUE).document(uid)
                            .set(queueData)
                            .addOnSuccessListener(unused -> callback.onWaiting())
                            .addOnFailureListener(e -> callback.onError(messageOf(e, "Greska pri ulasku u red cekanja")));
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Profil nije dostupan")));
    }

    private void createRegularPartyFromQueue(String ownerId, String ownerUsername, String guestId,
                                             String guestUsername, MatchmakingCallback callback) {
        DocumentReference partyRef = db.collection(PARTIES).document();
        DocumentReference ownerUserRef = db.collection(USERS).document(ownerId);
        DocumentReference guestUserRef = db.collection(USERS).document(guestId);
        DocumentReference ownerQueueRef = db.collection(QUEUE).document(ownerId);
        DocumentReference guestQueueRef = db.collection(QUEUE).document(guestId);
        DocumentReference sessionRef = db.collection(SESSIONS).document(partyRef.getId());

        db.runTransaction(transaction -> {
                    DocumentSnapshot ownerQueue = transaction.get(ownerQueueRef);
                    if (!ownerQueue.exists() || !"waiting".equals(ownerQueue.getString("status"))) {
                        throw abort("Protivnik vise nije u redu cekanja");
                    }

                    DocumentSnapshot ownerUser = transaction.get(ownerUserRef);
                    DocumentSnapshot guestUser = transaction.get(guestUserRef);
                    if (hasActiveParty(ownerUser) || hasActiveParty(guestUser)) {
                        throw abort("Jedan od igraca je vec u partiji");
                    }
                    if (intValue(ownerUser.get("tokens")) < REGULAR_TOKEN_COST
                            || intValue(guestUser.get("tokens")) < REGULAR_TOKEN_COST) {
                        throw abort("Oba igraca moraju imati bar 1 token");
                    }

                    Map<String, Object> party = buildPartyMap(
                            ownerId,
                            valueOrDefault(ownerUsername, "Igrac 1"),
                            guestId,
                            valueOrDefault(guestUsername, "Igrac 2"),
                            PartyData.TYPE_REGULAR,
                            true,
                            true
                    );
                    Map<String, Object> session = buildCompatSessionMap(ownerId, ownerUsername, guestId, guestUsername);

                    Map<String, Object> ownerUpdates = new HashMap<>();
                    ownerUpdates.put("tokens", FieldValue.increment(-REGULAR_TOKEN_COST));
                    ownerUpdates.put("activePartyId", partyRef.getId());
                    ownerUpdates.put("updatedAt", FieldValue.serverTimestamp());
                    Map<String, Object> guestUpdates = new HashMap<>();
                    guestUpdates.put("tokens", FieldValue.increment(-REGULAR_TOKEN_COST));
                    guestUpdates.put("activePartyId", partyRef.getId());
                    guestUpdates.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(ownerUserRef, ownerUpdates);
                    transaction.update(guestUserRef, guestUpdates);
                    transaction.set(partyRef, party);
                    transaction.set(sessionRef, session);
                    transaction.delete(ownerQueueRef);
                    transaction.delete(guestQueueRef);
                    return partyRef.getId();
                })
                .addOnSuccessListener(callback::onPartyReady)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Matchmaking nije uspeo")));
    }

    private Map<String, Object> buildPartyMap(String ownerId, String ownerUsername, String guestId,
                                              String guestUsername, String type, boolean countsForStats,
                                              boolean usesTokens) {
        Map<String, Object> party = new HashMap<>();
        party.put("ownerId", ownerId);
        party.put("ownerUsername", valueOrDefault(ownerUsername, "Igrac 1"));
        party.put("guestId", guestId);
        party.put("guestUsername", valueOrDefault(guestUsername, "Igrac 2"));
        party.put("type", type);
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
        party.put("countsForStats", countsForStats);
        party.put("usesTokens", usesTokens);
        party.put("rewardApplied", false);
        party.put("createdAt", FieldValue.serverTimestamp());
        party.put("updatedAt", FieldValue.serverTimestamp());
        return party;
    }

    private Map<String, Object> buildCompatSessionMap(String ownerId, String ownerUsername, String guestId,
                                                      String guestUsername) {
        Map<String, Object> session = new HashMap<>();
        session.put("ownerId", ownerId);
        session.put("ownerUsername", valueOrDefault(ownerUsername, "Igrac 1"));
        session.put("guestId", guestId);
        session.put("guestUsername", valueOrDefault(guestUsername, "Igrac 2"));
        session.put("status", "joined");
        session.put("code", "");
        session.put("selectedGame", "");
        session.put("createdAt", FieldValue.serverTimestamp());
        session.put("updatedAt", FieldValue.serverTimestamp());
        return session;
    }

    private boolean canAdvanceGame(PartyData party, String gameKey) {
        return PartyData.STATUS_IN_PROGRESS.equals(party.status)
                && gameKey != null
                && gameKey.equals(party.currentGameKey);
    }

    private boolean shouldApplyRewards(PartyData party, boolean finalGame) {
        return finalGame && party.isRegular() && party.countsForStats && !party.rewardApplied;
    }

    private Map<String, Object> buildAdvanceUpdates(PartyData party, String gameKey, int ownerScore, int guestScore,
                                                    int newOwnerTotal, int newGuestTotal, boolean finalGame) {
        int normalizedOwnerScore = normalizedOwnerScore(party, ownerScore, guestScore);
        int normalizedGuestScore = normalizedGuestScore(party, ownerScore, guestScore);
        Map<String, Object> updates = new HashMap<>();
        updates.put("gameScores." + gameKey + ".ownerScore", normalizedOwnerScore);
        updates.put("gameScores." + gameKey + ".guestScore", normalizedGuestScore);
        updates.put("gameScores." + gameKey + ".winner", determineSideWinner(normalizedOwnerScore, normalizedGuestScore));
        updates.put("gameScores." + gameKey + ".finishedAt", FieldValue.serverTimestamp());
        updates.put("ownerTotalScore", newOwnerTotal);
        updates.put("guestTotalScore", newGuestTotal);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        if (finalGame) {
            updates.put("status", PartyData.STATUS_FINISHED);
            updates.put("winner", determinePartyWinnerId(party, newOwnerTotal, newGuestTotal, party.forfeitedBy));
            updates.put("rewardApplied", party.isRegular() && party.countsForStats);
        } else {
            int nextIndex = party.currentGameIndex + 1;
            updates.put("currentGameIndex", nextIndex);
            updates.put("currentGameKey", PartyData.GAME_KEYS[nextIndex]);
        }
        return updates;
    }

    private int normalizedOwnerScore(PartyData party, int ownerScore, int guestScore) {
        if (party.ownerForfeited) {
            return 0;
        }
        if (party.guestForfeited) {
            return Math.max(ownerScore, guestScore);
        }
        return ownerScore;
    }

    private int normalizedGuestScore(PartyData party, int ownerScore, int guestScore) {
        if (party.guestForfeited) {
            return 0;
        }
        if (party.ownerForfeited) {
            return Math.max(ownerScore, guestScore);
        }
        return guestScore;
    }

    private void applyRegularRewards(Transaction transaction,
                                     DocumentReference ownerRef,
                                     DocumentSnapshot ownerUser,
                                     DocumentReference guestRef,
                                     DocumentSnapshot guestUser,
                                     PartyData party,
                                     int ownerTotal,
                                     int guestTotal,
                                     String forfeitedBy,
                                     boolean clearActiveParty) {
        boolean draw = forfeitedBy == null && ownerTotal == guestTotal;
        boolean ownerForfeited = forfeitedBy != null && forfeitedBy.equals(party.ownerId);
        boolean guestForfeited = forfeitedBy != null && forfeitedBy.equals(party.guestId);

        int ownerStarsDelta;
        int guestStarsDelta;
        Boolean ownerWon = null;
        Boolean guestWon = null;

        if (draw) {
            ownerStarsDelta = ownerTotal / 40;
            guestStarsDelta = guestTotal / 40;
        } else if (ownerForfeited) {
            ownerStarsDelta = 0;
            guestStarsDelta = 10 + guestTotal / 40;
            ownerWon = false;
            guestWon = true;
        } else if (guestForfeited) {
            ownerStarsDelta = 10 + ownerTotal / 40;
            guestStarsDelta = 0;
            ownerWon = true;
            guestWon = false;
        } else if (ownerTotal > guestTotal) {
            ownerStarsDelta = 10 + ownerTotal / 40;
            guestStarsDelta = -10 + guestTotal / 40;
            ownerWon = true;
            guestWon = false;
        } else {
            ownerStarsDelta = -10 + ownerTotal / 40;
            guestStarsDelta = 10 + guestTotal / 40;
            ownerWon = false;
            guestWon = true;
        }

        Map<String, Object> ownerUpdates = buildUserRewardUpdate(ownerUser, ownerStarsDelta, ownerWon);
        Map<String, Object> guestUpdates = buildUserRewardUpdate(guestUser, guestStarsDelta, guestWon);
        if (clearActiveParty) {
            ownerUpdates.put("activePartyId", null);
            guestUpdates.put("activePartyId", null);
        }
        transaction.update(ownerRef, ownerUpdates);
        transaction.update(guestRef, guestUpdates);
    }

    private Map<String, Object> buildUserRewardUpdate(DocumentSnapshot user, int starsDelta, Boolean won) {
        int currentStars = intValue(user.get("stars"));
        int currentProgress = intValue(user.get("starTokenProgress"));
        int newStars = Math.max(0, currentStars + starsDelta);
        Map<String, Object> updates = LeagueProgressionHelper.buildStarsAndLeagueUpdate(
                currentStars, newStars);

        updates.put("matchesPlayed", FieldValue.increment(1));

        if (won != null) {
            updates.put(won ? "wins" : "losses", FieldValue.increment(1));
        }

        if (starsDelta > 0) {
            int newProgress = currentProgress + starsDelta;
            int tokenBonus = newProgress / 50;
            updates.put("starTokenProgress", newProgress % 50);
            if (tokenBonus > 0) {
                updates.put("tokens", FieldValue.increment(tokenBonus));
            }
        }

        String currentMonth = currentMonthKey();
        int currentMonthlyStars = currentMonth.equals(user.getString("monthlyRankMonth"))
                ? intValue(user.get("monthlyStars"))
                : 0;
        updates.put("monthlyRankMonth", currentMonth);
        updates.put("monthlyStars", Math.max(0, currentMonthlyStars + starsDelta));
        return updates;
    }

    private String determineSideWinner(int ownerScore, int guestScore) {
        if (ownerScore > guestScore) {
            return "owner";
        }
        if (guestScore > ownerScore) {
            return "guest";
        }
        return "draw";
    }

    private String determinePartyWinnerId(PartyData party, int ownerTotal, int guestTotal, String forfeitedBy) {
        if (forfeitedBy != null) {
            if (forfeitedBy.equals(party.ownerId)) {
                return party.guestId;
            }
            if (forfeitedBy.equals(party.guestId)) {
                return party.ownerId;
            }
        }
        if (ownerTotal > guestTotal) {
            return party.ownerId;
        }
        if (guestTotal > ownerTotal) {
            return party.guestId;
        }
        return "draw";
    }

    private FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.ABORTED);
    }

    private void clearActiveParty(Transaction transaction, DocumentReference ownerRef, DocumentReference guestRef) {
        transaction.update(ownerRef,
                "activePartyId", null,
                "updatedAt", FieldValue.serverTimestamp());
        transaction.update(guestRef,
                "activePartyId", null,
                "updatedAt", FieldValue.serverTimestamp());
    }

    private boolean hasActiveParty(DocumentSnapshot user) {
        String activePartyId = user.getString("activePartyId");
        return activePartyId != null && !activePartyId.trim().isEmpty();
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String currentMonthKey() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private String messageOf(Exception e, String fallback) {
        return e != null && e.getMessage() != null ? e.getMessage() : fallback;
    }
}
