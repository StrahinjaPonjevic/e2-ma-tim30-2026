package com.example.slagalica.party;

import com.example.slagalica.leagues.LeagueProgressionHelper;
import com.example.slagalica.missions.MissionsRepository;
import com.example.slagalica.ranking.CycleUtils;
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

    private static final long QUEUE_MAX_AGE_MS = 45_000L;

    public PartyRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void findRandomOpponentOrWait(String uid, String username, MatchmakingCallback callback) {
        db.collection(USERS).document(uid).update("activePartyId", null);
        long minCreatedAtMs = System.currentTimeMillis() - QUEUE_MAX_AGE_MS;
        db.collection(QUEUE)
                .whereEqualTo("status", "waiting")
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    QueryDocumentSnapshot opponent = null;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (uid.equals(doc.getId())) {
                            continue;
                        }

                        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
                        if (createdAt != null && createdAt.toDate().getTime() < minCreatedAtMs) {
                            // Clean up stale queue document
                            db.collection(QUEUE).document(doc.getId()).delete();
                            continue;
                        }

                        opponent = doc;
                        break;
                    }

                    if (opponent == null) {
                        addCurrentUserToQueue(uid, username, callback);
                        return;
                    }

                    String opponentUid = opponent.getId();
                    String opponentUsername = valueOrDefault(opponent.getString("username"), "Igrac");
                    createRegularPartyFromQueue(opponentUid, opponentUsername, uid, username, new MatchmakingCallback() {
                        @Override
                        public void onPartyReady(String partyId) {
                            callback.onPartyReady(partyId);
                        }

                        @Override
                        public void onWaiting() {
                            callback.onWaiting();
                        }

                        @Override
                        public void onError(String message) {
                            addCurrentUserToQueue(uid, username, callback);
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Greska pri trazenju protivnika")));
    }

    public void cancelQueue(String uid, OperationCallback callback) {
        db.collection(QUEUE).document(uid)
                .delete()
                .addOnSuccessListener(unused -> {
                    db.collection(USERS).document(uid).update("activePartyId", null);
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

    public static class MultiListenerRegistration implements ListenerRegistration {
        private final java.util.List<ListenerRegistration> registrations = new java.util.ArrayList<>();
        private boolean removed = false;

        public synchronized void add(ListenerRegistration reg) {
            if (reg == null) return;
            if (removed) {
                reg.remove();
            } else {
                registrations.add(reg);
            }
        }

        @Override
        public synchronized void remove() {
            removed = true;
            for (ListenerRegistration reg : registrations) {
                if (reg != null) {
                    reg.remove();
                }
            }
            registrations.clear();
        }
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
        return db.collection(QUEUE).document(ownerId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri cekanju protivnika"));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    String status = snapshot.getString("status");
                    String partyId = snapshot.getString("partyId");
                    if ("matched".equals(status) && partyId != null && !partyId.trim().isEmpty()) {
                        db.collection(PARTIES).document(partyId).get()
                                .addOnSuccessListener(partySnap -> {
                                    if (partySnap == null || !partySnap.exists()) return;
                                    PartyData party = PartyData.fromSnapshot(partySnap);
                                    if (PartyData.STATUS_IN_PROGRESS.equals(party.status)) {
                                        db.collection(QUEUE).document(ownerId).delete();
                                        listener.onPartyChanged(party);
                                    }
                                })
                                .addOnFailureListener(e -> listener.onError(messageOf(e, "Greska pri ucitavanju partije")));
                    }
                });
    }

    public void clearUserActiveParty(String uid, OperationCallback callback) {
        if (uid == null) return;
        db.collection(USERS).document(uid).update("activePartyId", null)
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Greska"));
                });
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

                    int newOwnerTotal = calculateTotalScore(party, gameKey, ownerScore, true);
                    int newGuestTotal = calculateTotalScore(party, gameKey, guestScore, false);
                    Map<String, Object> updates = buildAdvanceUpdates(party, gameKey, ownerScore, guestScore,
                            newOwnerTotal, newGuestTotal, finalGame);
                    transaction.update(partyRef, updates);

                    if (applyRewards) {
                        applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                newOwnerTotal, newGuestTotal, party.forfeitedBy, true);
                    } else if (finalGame) {
                        clearActiveParty(transaction, ownerRef, guestRef);
                    }
                    if (!finalGame) {
                        return null;
                    }
                    String finishedWinnerId = determinePartyWinnerId(party, newOwnerTotal, newGuestTotal, party.forfeitedBy);
                    return new String[]{party.type, finishedWinnerId, party.ownerId, party.guestId};
                })
                .addOnSuccessListener(result -> {
                    if (result != null) {
                        String finishedType = result[0];
                        String finishedWinnerId = result[1];
                        if (PartyData.TYPE_REGULAR.equals(finishedType)
                                && finishedWinnerId != null && !"draw".equals(finishedWinnerId)) {
                            MissionsRepository.markPartyWon(finishedWinnerId);
                        }
                        if (PartyData.TYPE_FRIENDLY.equals(finishedType)) {
                            MissionsRepository.markFriendlyPlayed(result[2]);
                            MissionsRepository.markFriendlyPlayed(result[3]);
                        }
                    }
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

                        int newOwnerTotal = calculateTotalScore(party, gameKey, ownerScore, true);
                        int newGuestTotal = calculateTotalScore(party, gameKey, guestScore, false);
                        updates.putAll(buildAdvanceUpdates(party, gameKey, ownerScore, guestScore,
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
        forfeitPartyWithCurrentGameScore(partyId, null, forfeitedBy, null, null, callback);
    }

    public void forfeitPartyWithCurrentGameScore(String partyId, String gameKey, String forfeitedBy,
                                                Integer currentOwnerGameScore, Integer currentGuestGameScore,
                                                OperationCallback callback) {
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

                    String originalForfeiter = party.forfeitedBy != null ? party.forfeitedBy : forfeitedBy;
                    boolean alreadyHadForfeit = party.ownerForfeited || party.guestForfeited;
                    boolean bothForfeitedNow = alreadyHadForfeit || (ownerForfeited && guestForfeited);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("forfeitedBy", originalForfeiter);
                    updates.put(ownerForfeited ? "ownerForfeited" : "guestForfeited", true);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    party.ownerForfeited = ownerForfeited || party.ownerForfeited;
                    party.guestForfeited = guestForfeited || party.guestForfeited;

                    String activeGameKey = gameKey != null ? gameKey : party.currentGameKey;
                    int ownerGameScore = currentOwnerGameScore != null ? currentOwnerGameScore
                            : (party.gameScores != null && party.gameScores.containsKey(activeGameKey) && party.gameScores.get(activeGameKey) instanceof Map
                                ? intValue(((Map<?, ?>) party.gameScores.get(activeGameKey)).get("ownerScore")) : 0);
                    int guestGameScore = currentGuestGameScore != null ? currentGuestGameScore
                            : (party.gameScores != null && party.gameScores.containsKey(activeGameKey) && party.gameScores.get(activeGameKey) instanceof Map
                                ? intValue(((Map<?, ?>) party.gameScores.get(activeGameKey)).get("guestScore")) : 0);

                    int newOwnerTotal = calculateTotalScore(party, activeGameKey, ownerGameScore, true);
                    int newGuestTotal = calculateTotalScore(party, activeGameKey, guestGameScore, false);

                    updates.put("gameScores." + activeGameKey + ".ownerScore", ownerGameScore);
                    updates.put("gameScores." + activeGameKey + ".guestScore", guestGameScore);
                    updates.put("gameScores." + activeGameKey + ".finishedAt", FieldValue.serverTimestamp());
                    updates.put("ownerTotalScore", newOwnerTotal);
                    updates.put("guestTotalScore", newGuestTotal);

                    if (bothForfeitedNow) {
                        // Both players have now forfeited/left: finalize party immediately
                        boolean firstForfeiterIsOwner = party.ownerId.equals(originalForfeiter);
                        updates.put("status", PartyData.STATUS_FINISHED);
                        updates.put("winner", firstForfeiterIsOwner ? party.guestId : party.ownerId);
                        updates.put("rewardApplied", party.isRegular() && party.countsForStats);

                        DocumentReference ownerRef = db.collection(USERS).document(party.ownerId);
                        DocumentReference guestRef = db.collection(USERS).document(party.guestId);
                        boolean applyRewards = party.isRegular() && party.countsForStats && !party.rewardApplied;
                        if (applyRewards) {
                            DocumentSnapshot ownerUser = transaction.get(ownerRef);
                            DocumentSnapshot guestUser = transaction.get(guestRef);
                            applyRegularRewards(transaction, ownerRef, ownerUser, guestRef, guestUser, party,
                                    newOwnerTotal, newGuestTotal, originalForfeiter, true);
                        } else {
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
                    if (referenceMs <= 0L || System.currentTimeMillis() - referenceMs < staleAfterMs) {
                        return null;
                    }

                    Map<String, Object> gameScore = party.currentGameScoreMap();
                    int rawOwnerScore = intValue(gameScore.get("ownerScore"));
                    int rawGuestScore = intValue(gameScore.get("guestScore"));
                    int newOwnerTotal = calculateTotalScore(party, party.currentGameKey, rawOwnerScore, true);
                    int newGuestTotal = calculateTotalScore(party, party.currentGameKey, rawGuestScore, false);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("gameScores." + party.currentGameKey + ".ownerScore", rawOwnerScore);
                    updates.put("gameScores." + party.currentGameKey + ".guestScore", rawGuestScore);
                    updates.put("gameScores." + party.currentGameKey + ".winner",
                            determineSideWinner(rawOwnerScore, rawGuestScore));
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
                    if (intValue(userSnap.get("tokens")) < REGULAR_TOKEN_COST) {
                        callback.onError("Nemate dovoljno tokena za regularnu partiju.");
                        return;
                    }

                    String activePartyId = userSnap.getString("activePartyId");
                    if (activePartyId != null && !activePartyId.trim().isEmpty()) {
                        db.collection(PARTIES).document(activePartyId).get()
                                .addOnSuccessListener(partySnap -> {
                                    if (partySnap.exists() && PartyData.STATUS_IN_PROGRESS.equals(partySnap.getString("status"))) {
                                        callback.onError("Vec ucestvujete u partiji.");
                                    } else {
                                        db.collection(USERS).document(uid).update("activePartyId", null);
                                        enqueueUser(uid, username, callback);
                                    }
                                })
                                .addOnFailureListener(e -> enqueueUser(uid, username, callback));
                        return;
                    }

                    enqueueUser(uid, username, callback);
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Profil nije dostupan")));
    }

    private void enqueueUser(String uid, String username, MatchmakingCallback callback) {
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("uid", uid);
        queueData.put("username", valueOrDefault(username, "Igrac"));
        queueData.put("status", "waiting");
        queueData.put("createdAt", FieldValue.serverTimestamp());

        db.collection(QUEUE).document(uid)
                .set(queueData)
                .addOnSuccessListener(unused -> callback.onWaiting())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Greska pri ulasku u red cekanja")));
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

                    Map<String, Object> queueMatchUpdate = new HashMap<>();
                    queueMatchUpdate.put("uid", ownerId);
                    queueMatchUpdate.put("status", "matched");
                    queueMatchUpdate.put("partyId", partyRef.getId());
                    queueMatchUpdate.put("matchedAt", FieldValue.serverTimestamp());
                    transaction.set(ownerQueueRef, queueMatchUpdate);
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
        Map<String, Object> updates = new HashMap<>();
        updates.put("gameScores." + gameKey + ".ownerScore", ownerScore);
        updates.put("gameScores." + gameKey + ".guestScore", guestScore);
        updates.put("gameScores." + gameKey + ".winner", determineSideWinner(ownerScore, guestScore));
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

    private int calculateTotalScore(PartyData party, String activeGameKey, int activeGameScore, boolean forOwner) {
        int total = 0;
        for (String key : PartyData.GAME_KEYS) {
            if (key.equals(activeGameKey)) {
                total += activeGameScore;
            } else if (party.gameScores != null && party.gameScores.containsKey(key)) {
                Object obj = party.gameScores.get(key);
                if (obj instanceof Map) {
                    Map<?, ?> gs = (Map<?, ?>) obj;
                    total += intValue(gs.get(forOwner ? "ownerScore" : "guestScore"));
                }
            }
        }
        return total;
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
        int ownerStarsEarned;
        int guestStarsEarned;
        Boolean ownerWon = null;
        Boolean guestWon = null;

        if (draw) {
            ownerStarsDelta = ownerTotal / 40;
            guestStarsDelta = guestTotal / 40;
            ownerStarsEarned = ownerStarsDelta;
            guestStarsEarned = guestStarsDelta;
        } else if (ownerForfeited) {
            // Rule f: Napuštanjem igre igrač gubi partiju i ne dobija zvezde.
            ownerStarsDelta = -10;
            ownerStarsEarned = 0;
            guestStarsDelta = 10 + guestTotal / 40;
            guestStarsEarned = guestStarsDelta;
            ownerWon = false;
            guestWon = true;
        } else if (guestForfeited) {
            ownerStarsDelta = 10 + ownerTotal / 40;
            ownerStarsEarned = ownerStarsDelta;
            guestStarsDelta = -10;
            guestStarsEarned = 0;
            ownerWon = true;
            guestWon = false;
        } else if (ownerTotal > guestTotal) {
            ownerStarsDelta = 10 + ownerTotal / 40;
            ownerStarsEarned = ownerStarsDelta;
            guestStarsDelta = -10 + guestTotal / 40;
            guestStarsEarned = guestTotal / 40;
            ownerWon = true;
            guestWon = false;
        } else {
            ownerStarsDelta = -10 + ownerTotal / 40;
            ownerStarsEarned = ownerTotal / 40;
            guestStarsDelta = 10 + guestTotal / 40;
            guestStarsEarned = guestStarsDelta;
            ownerWon = false;
            guestWon = true;
        }

        Map<String, Object> ownerUpdates = buildUserRewardUpdate(ownerUser, ownerStarsDelta, ownerStarsEarned, ownerWon);
        Map<String, Object> guestUpdates = buildUserRewardUpdate(guestUser, guestStarsDelta, guestStarsEarned, guestWon);
        if (clearActiveParty) {
            ownerUpdates.put("activePartyId", null);
            guestUpdates.put("activePartyId", null);
        }
        transaction.update(ownerRef, ownerUpdates);
        transaction.update(guestRef, guestUpdates);
    }

    private Map<String, Object> buildUserRewardUpdate(DocumentSnapshot user, int starsDelta, int starsEarned, Boolean won) {
        int currentStars = intValue(user.get("stars"));
        int currentProgress = intValue(user.get("starTokenProgress"));
        int newStars = Math.max(0, currentStars + starsDelta);
        Map<String, Object> updates = LeagueProgressionHelper.buildStarsAndLeagueUpdate(
                currentStars, newStars);

        updates.put("matchesPlayed", FieldValue.increment(1));

        if (won != null) {
            updates.put(won ? "wins" : "losses", FieldValue.increment(1));
        }

        if (starsEarned > 0) {
            int newProgress = currentProgress + starsEarned;
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

        String currentWeek = CycleUtils.currentWeekKey();
        int currentWeeklyStars = currentWeek.equals(user.getString("weeklyRankKey"))
                ? intValue(user.get("weeklyStars"))
                : 0;
        updates.put("weeklyRankKey", currentWeek);
        updates.put("weeklyStars", Math.max(0, currentWeeklyStars + starsDelta));
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
