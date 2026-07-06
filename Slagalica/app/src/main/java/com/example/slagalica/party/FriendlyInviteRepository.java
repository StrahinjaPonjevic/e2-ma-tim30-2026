package com.example.slagalica.party;

import android.content.Context;

import com.example.slagalica.notifications.NotificationChannelManager;
import com.example.slagalica.notifications.NotificationHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendlyInviteRepository {
    private static final String USERS = "users";
    private static final String FRIENDS = "friends";
    private static final String INVITES = "friendly_invites";
    private static final String PARTIES = "parties";
    private static final String SESSIONS = "sessions";
    private static final long INVITE_TIMEOUT_MS = 10_000L;

    private static ListenerRegistration notificationListener;
    private static boolean notificationInitialSnapshot = true;

    private final FirebaseFirestore db;

    public interface UsersCallback {
        void onUsers(List<UserSummary> users);
        void onError(String message);
    }

    public interface InviteListener {
        void onInvite(FriendlyInviteData invite);
        void onNone();
        void onError(String message);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface SendInviteCallback {
        void onSuccess(String inviteId);
        void onError(String message);
    }

    public interface PartyReadyCallback {
        void onSuccess(String partyId);
        void onError(String message);
    }

    public interface OutgoingInviteListener {
        void onAccepted(String partyId);
        void onDeclined();
        void onExpired();
        void onCancelled();
        void onPending();
        void onError(String message);
    }

    public FriendlyInviteRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void listUsersInRegion(String region, String currentUid, UsersCallback callback) {
        db.collection(USERS)
                .whereEqualTo("region", region)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<UserSummary> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (doc.getId().equals(currentUid)) {
                            continue;
                        }
                        users.add(new UserSummary(
                                doc.getId(),
                                valueOrDefault(doc.getString("username"), "Igrac"),
                                valueOrDefault(doc.getString("region"), region)
                        ));
                    }
                    callback.onUsers(users);
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Korisnici nisu dostupni")));
    }

    public ListenerRegistration listenIncomingInvite(String uid, InviteListener listener) {
        return db.collection(INVITES)
                .whereEqualTo("inviteeId", uid)
                .whereEqualTo("status", FriendlyInviteData.STATUS_PENDING)
                .limit(5)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri osluskivanju poziva"));
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        listener.onNone();
                        return;
                    }
                    FriendlyInviteData newest = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FriendlyInviteData invite = FriendlyInviteData.fromSnapshot(doc);
                        if (invite.isExpired()) {
                            expireInvite(invite.inviteId, null);
                            continue;
                        }
                        newest = invite;
                    }
                    if (newest == null) {
                        listener.onNone();
                    } else {
                        listener.onInvite(newest);
                    }
                });
    }

    public void sendInvite(String inviterId, String inviterUsername, String inviteeId,
                           String inviteeUsername, SendInviteCallback callback) {
        if (inviterId == null || inviterId.equals(inviteeId)) {
            callback.onError("Izaberite drugog igraca");
            return;
        }

        createInvite(inviterId, inviterUsername, inviteeId, inviteeUsername, callback);
    }

    private void createInvite(String inviterId, String inviterUsername, String inviteeId,
                              String inviteeUsername, SendInviteCallback callback) {
        DocumentReference inviteRef = db.collection(INVITES).document(inviterId);
        DocumentReference friendshipRef = db.collection(USERS).document(inviterId)
                .collection(FRIENDS).document(inviteeId);
        DocumentReference inviterRef = db.collection(USERS).document(inviterId);
        DocumentReference inviteeRef = db.collection(USERS).document(inviteeId);

        Map<String, Object> invite = new HashMap<>();
        invite.put("inviterId", inviterId);
        invite.put("inviterUsername", valueOrDefault(inviterUsername, "Igrac"));
        invite.put("inviteeId", inviteeId);
        invite.put("inviteeUsername", valueOrDefault(inviteeUsername, "Igrac"));
        invite.put("status", FriendlyInviteData.STATUS_PENDING);
        invite.put("partyId", null);
        invite.put("createdAt", FieldValue.serverTimestamp());
        invite.put("updatedAt", FieldValue.serverTimestamp());
        invite.put("expiresAt", new Timestamp(new Date(System.currentTimeMillis() + INVITE_TIMEOUT_MS)));

        db.runTransaction(transaction -> {
                    DocumentSnapshot existingInvite = transaction.get(inviteRef);
                    DocumentSnapshot friendship = transaction.get(friendshipRef);
                    if (!friendship.exists()) {
                        throw abort("Igraca najpre dodajte u prijatelje");
                    }
                    DocumentSnapshot inviter = transaction.get(inviterRef);
                    DocumentSnapshot invitee = transaction.get(inviteeRef);
                    if (!inviter.exists() || !invitee.exists()) {
                        throw abort("Korisnik nije pronadjen");
                    }
                    if (hasActiveParty(inviter)) {
                        throw abort("Vec ucestvujete u partiji");
                    }
                    if (!Boolean.TRUE.equals(invitee.getBoolean("isLoggedIn"))) {
                        throw abort("Prijatelj nije ulogovan");
                    }
                    if (hasActiveParty(invitee)) {
                        throw abort("Prijatelj je vec u partiji");
                    }
                    if (existingInvite.exists()) {
                        FriendlyInviteData existing = FriendlyInviteData.fromSnapshot(existingInvite);
                        if (FriendlyInviteData.STATUS_PENDING.equals(existing.status) && !existing.isExpired()) {
                            throw abort("Vec imate poslat zahtev za partiju");
                        }
                    }
                    transaction.set(inviteRef, invite);
                    return inviteRef.getId();
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Poziv nije poslat")));
    }

    public ListenerRegistration listenOutgoingInvite(String inviteId, OutgoingInviteListener listener) {
        return db.collection(INVITES).document(inviteId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri osluskivanju poslatog poziva"));
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onExpired();
                        return;
                    }

                    FriendlyInviteData invite = FriendlyInviteData.fromSnapshot(snapshot);
                    if (FriendlyInviteData.STATUS_DECLINED.equals(invite.status)) {
                        listener.onDeclined();
                        return;
                    }
                    if (FriendlyInviteData.STATUS_CANCELLED.equals(invite.status)) {
                        listener.onCancelled();
                        return;
                    }
                    if (invite.isExpired() || FriendlyInviteData.STATUS_EXPIRED.equals(invite.status)) {
                        listener.onExpired();
                        return;
                    }
                    if (FriendlyInviteData.STATUS_ACCEPTED.equals(invite.status)
                            && invite.partyId != null
                            && !invite.partyId.trim().isEmpty()) {
                        listener.onAccepted(invite.partyId);
                        return;
                    }
                    listener.onPending();
                });
    }

    public void acceptInvite(String inviteId, String uid, PartyReadyCallback callback) {
        DocumentReference inviteRef = db.collection(INVITES).document(inviteId);
        DocumentReference partyRef = db.collection(PARTIES).document();
        DocumentReference sessionRef = db.collection(SESSIONS).document(partyRef.getId());

        db.runTransaction(transaction -> {
                    DocumentSnapshot inviteSnap = transaction.get(inviteRef);
                    if (!inviteSnap.exists()) {
                        throw abort("Poziv nije pronadjen");
                    }
                    FriendlyInviteData invite = FriendlyInviteData.fromSnapshot(inviteSnap);
                    if (!uid.equals(invite.inviteeId)) {
                        throw abort("Poziv nije namenjen ovom igracu");
                    }
                    if (!FriendlyInviteData.STATUS_PENDING.equals(invite.status)) {
                        throw abort("Poziv vise nije aktivan");
                    }
                    if (invite.isExpired()) {
                        transaction.update(inviteRef,
                                "status", FriendlyInviteData.STATUS_EXPIRED,
                                "updatedAt", FieldValue.serverTimestamp());
                        throw abort("Poziv je istekao");
                    }

                    DocumentReference txInviterRef = db.collection(USERS).document(invite.inviterId);
                    DocumentReference txInviteeRef = db.collection(USERS).document(invite.inviteeId);
                    DocumentSnapshot inviter = transaction.get(txInviterRef);
                    DocumentSnapshot invitee = transaction.get(txInviteeRef);
                    if (!inviter.exists() || !invitee.exists()) {
                        throw abort("Korisnik nije pronadjen");
                    }
                    if (hasActiveParty(inviter) || hasActiveParty(invitee)) {
                        throw abort("Jedan od igraca je vec u partiji");
                    }

                    transaction.set(partyRef, buildPartyMap(invite));
                    transaction.set(sessionRef, buildSessionMap(invite));
                    transaction.update(inviteRef,
                            "status", FriendlyInviteData.STATUS_ACCEPTED,
                            "partyId", partyRef.getId(),
                            "updatedAt", FieldValue.serverTimestamp());
                    transaction.update(txInviterRef,
                            "activePartyId", partyRef.getId(),
                            "updatedAt", FieldValue.serverTimestamp());
                    transaction.update(txInviteeRef,
                            "activePartyId", partyRef.getId(),
                            "updatedAt", FieldValue.serverTimestamp());
                    return partyRef.getId();
                })
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Prihvatanje poziva nije uspelo")));
    }

    public void declineInvite(String inviteId, OperationCallback callback) {
        updateInviteStatus(inviteId, FriendlyInviteData.STATUS_DECLINED, callback);
    }

    public void cancelInvite(String inviteId, String inviterId, OperationCallback callback) {
        DocumentReference inviteRef = db.collection(INVITES).document(inviteId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot inviteSnap = transaction.get(inviteRef);
                    if (!inviteSnap.exists()) {
                        throw abort("Poziv nije pronadjen");
                    }
                    FriendlyInviteData invite = FriendlyInviteData.fromSnapshot(inviteSnap);
                    if (!inviterId.equals(invite.inviterId)) {
                        throw abort("Samo posiljalac moze prekinuti zahtev");
                    }
                    if (!FriendlyInviteData.STATUS_PENDING.equals(invite.status)) {
                        throw abort("Poziv vise nije aktivan");
                    }
                    if (invite.isExpired()) {
                        transaction.update(inviteRef,
                                "status", FriendlyInviteData.STATUS_EXPIRED,
                                "updatedAt", FieldValue.serverTimestamp());
                        return null;
                    }
                    transaction.update(inviteRef,
                            "status", FriendlyInviteData.STATUS_CANCELLED,
                            "updatedAt", FieldValue.serverTimestamp());
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Zahtev nije prekinut"));
                });
    }

    public void expireInvite(String inviteId, OperationCallback callback) {
        updateInviteStatus(inviteId, FriendlyInviteData.STATUS_EXPIRED, callback);
    }

    public static void startNotificationListener(Context context, String uid) {
        stopNotificationListener();
        notificationInitialSnapshot = true;
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        notificationListener = db.collection(INVITES)
                .whereEqualTo("inviteeId", uid)
                .whereEqualTo("status", FriendlyInviteData.STATUS_PENDING)
                .limit(5)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (notificationInitialSnapshot) {
                        notificationInitialSnapshot = false;
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FriendlyInviteData invite = FriendlyInviteData.fromSnapshot(doc);
                        if (!invite.isExpired()) {
                            NotificationHelper.sendAndStore(
                                    appContext,
                                    uid,
                                    NotificationChannelManager.CHANNEL_OTHER,
                                    "Prijateljska partija",
                                    invite.inviterUsername + " vas poziva na partiju.",
                                    Math.abs(doc.getId().hashCode()),
                                    "friendly_invite",
                                    doc.getId()
                            );
                            return;
                        }
                    }
                });
    }

    public static void stopNotificationListener() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
        notificationInitialSnapshot = true;
    }

    private void updateInviteStatus(String inviteId, String status, OperationCallback callback) {
        db.collection(INVITES).document(inviteId)
                .update("status", status, "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(messageOf(e, "Poziv nije azuriran"));
                });
    }

    private Map<String, Object> buildPartyMap(FriendlyInviteData invite) {
        Map<String, Object> party = new HashMap<>();
        party.put("ownerId", invite.inviterId);
        party.put("ownerUsername", valueOrDefault(invite.inviterUsername, "Igrac 1"));
        party.put("guestId", invite.inviteeId);
        party.put("guestUsername", valueOrDefault(invite.inviteeUsername, "Igrac 2"));
        party.put("type", PartyData.TYPE_FRIENDLY);
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
        party.put("countsForStats", false);
        party.put("usesTokens", false);
        party.put("rewardApplied", false);
        party.put("createdAt", FieldValue.serverTimestamp());
        party.put("updatedAt", FieldValue.serverTimestamp());
        return party;
    }

    private Map<String, Object> buildSessionMap(FriendlyInviteData invite) {
        Map<String, Object> session = new HashMap<>();
        session.put("ownerId", invite.inviterId);
        session.put("ownerUsername", valueOrDefault(invite.inviterUsername, "Igrac 1"));
        session.put("guestId", invite.inviteeId);
        session.put("guestUsername", valueOrDefault(invite.inviteeUsername, "Igrac 2"));
        session.put("status", "joined");
        session.put("code", "");
        session.put("selectedGame", "");
        session.put("createdAt", FieldValue.serverTimestamp());
        session.put("updatedAt", FieldValue.serverTimestamp());
        return session;
    }

    private FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.ABORTED);
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private boolean hasActiveParty(DocumentSnapshot user) {
        String activePartyId = user.getString("activePartyId");
        return activePartyId != null && !activePartyId.trim().isEmpty();
    }

    private String messageOf(Exception e, String fallback) {
        return e != null && e.getMessage() != null ? e.getMessage() : fallback;
    }

    public static final class UserSummary {
        public final String uid;
        public final String username;
        public final String region;

        public UserSummary(String uid, String username, String region) {
            this.uid = uid;
            this.username = username;
            this.region = region;
        }
    }
}
