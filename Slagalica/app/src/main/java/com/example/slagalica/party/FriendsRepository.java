package com.example.slagalica.party;

import com.example.slagalica.leagues.LeagueUiHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FriendsRepository {
    private static final String USERS = "users";
    private static final String FRIENDS = "friends";
    private static final String FRIEND_REQUESTS = "friend_requests";
    private static final String QR_PREFIX = "slagalica:user:";
    public static final String REQUEST_PENDING = "pending";
    public static final String REQUEST_ACCEPTED = "accepted";
    public static final String REQUEST_DECLINED = "declined";

    private final FirebaseFirestore db;

    public interface OperationCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface FriendsCallback {
        void onFriends(List<FriendSummary> friends);
        void onError(String message);
    }

    public interface SearchCallback {
        void onFound(FriendSummary user, boolean alreadyFriend);
        void onError(String message);
    }

    public interface FriendRequestListener {
        void onRequest(FriendRequest request);
        void onNone();
        void onError(String message);
    }

    private interface RankCallback {
        void onRanks(Map<String, Integer> ranks);
        void onError(String message);
    }

    public FriendsRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void listFriends(String currentUid, FriendsCallback callback) {
        db.collection(USERS).document(currentUid).collection(FRIENDS)
                .get()
                .addOnSuccessListener(friendDocs -> {
                    if (friendDocs == null || friendDocs.isEmpty()) {
                        callback.onFriends(new ArrayList<>());
                        return;
                    }

                    loadMonthlyRanks(new RankCallback() {
                        @Override
                        public void onRanks(Map<String, Integer> ranks) {
                            List<FriendSummary> friends = new ArrayList<>();
                            AtomicInteger remaining = new AtomicInteger(friendDocs.size());
                            for (QueryDocumentSnapshot friendDoc : friendDocs) {
                                String friendUid = friendDoc.getId();
                                db.collection(USERS).document(friendUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                friends.add(FriendSummary.fromUserSnapshot(
                                                        userDoc,
                                                        ranks.containsKey(friendUid) ? ranks.get(friendUid) : 0
                                                ));
                                            }
                                            if (remaining.decrementAndGet() == 0) {
                                                friends.sort(Comparator.comparing(friend -> friend.username.toLowerCase(Locale.ROOT)));
                                                callback.onFriends(friends);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            if (remaining.decrementAndGet() == 0) {
                                                friends.sort(Comparator.comparing(friend -> friend.username.toLowerCase(Locale.ROOT)));
                                                callback.onFriends(friends);
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Prijatelji nisu dostupni")));
    }

    public void searchByUsername(String currentUid, String username, SearchCallback callback) {
        String cleaned = username != null ? username.trim() : "";
        if (cleaned.isEmpty()) {
            callback.onError("Unesite korisnicko ime");
            return;
        }

        db.collection(USERS)
                .whereEqualTo("username", cleaned)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onError("Korisnik nije pronadjen");
                        return;
                    }
                    DocumentSnapshot userDoc = snapshot.getDocuments().get(0);
                    if (userDoc.getId().equals(currentUid)) {
                        callback.onError("Ne mozete dodati sebe");
                        return;
                    }

                    db.collection(USERS).document(currentUid).collection(FRIENDS).document(userDoc.getId())
                            .get()
                            .addOnSuccessListener(friendDoc -> callback.onFound(
                                    FriendSummary.fromUserSnapshot(userDoc, 0),
                                    friendDoc.exists()
                            ))
                            .addOnFailureListener(e -> callback.onError(messageOf(e, "Provera prijatelja nije uspela")));
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Pretraga nije uspela")));
    }

    public ListenerRegistration listenIncomingFriendRequest(String currentUid, FriendRequestListener listener) {
        return db.collection(FRIEND_REQUESTS)
                .whereEqualTo("recipientId", currentUid)
                .whereEqualTo("status", REQUEST_PENDING)
                .limit(5)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(messageOf(error, "Greska pri osluskivanju zahteva za prijateljstvo"));
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        listener.onNone();
                        return;
                    }
                    listener.onRequest(FriendRequest.fromSnapshot(snapshot.getDocuments().get(0)));
                });
    }

    public void sendFriendRequest(String currentUid, String recipientUid, OperationCallback callback) {
        if (currentUid == null || recipientUid == null || currentUid.equals(recipientUid)) {
            callback.onError("Izaberite drugog igraca");
            return;
        }

        DocumentReference requesterRef = db.collection(USERS).document(currentUid);
        DocumentReference recipientRef = db.collection(USERS).document(recipientUid);
        DocumentReference currentFriendRef = requesterRef.collection(FRIENDS).document(recipientUid);
        DocumentReference requestRef = db.collection(FRIEND_REQUESTS).document(requestId(currentUid, recipientUid));
        DocumentReference inverseRequestRef = db.collection(FRIEND_REQUESTS).document(requestId(recipientUid, currentUid));

        db.runTransaction(transaction -> {
                    DocumentSnapshot requester = transaction.get(requesterRef);
                    DocumentSnapshot recipient = transaction.get(recipientRef);
                    DocumentSnapshot existingFriend = transaction.get(currentFriendRef);
                    DocumentSnapshot existingRequest = transaction.get(requestRef);
                    DocumentSnapshot inverseRequest = transaction.get(inverseRequestRef);
                    if (!requester.exists() || !recipient.exists()) {
                        throw abort("Korisnik nije pronadjen");
                    }
                    if (existingFriend.exists()) {
                        throw abort("Igrac je vec u listi prijatelja");
                    }
                    if (isPendingRequest(existingRequest)) {
                        throw abort("Zahtev je vec poslat");
                    }
                    if (isPendingRequest(inverseRequest)) {
                        throw abort("Ovaj igrac vam je vec poslao zahtev. Prihvatite ga.");
                    }

                    Map<String, Object> request = new HashMap<>();
                    request.put("requesterId", currentUid);
                    request.put("requesterUsername", valueOrDefault(requester.getString("username"), "Igrac"));
                    request.put("recipientId", recipientUid);
                    request.put("recipientUsername", valueOrDefault(recipient.getString("username"), "Igrac"));
                    request.put("status", REQUEST_PENDING);
                    request.put("createdAt", FieldValue.serverTimestamp());
                    request.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.set(requestRef, request);
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Zahtev za prijateljstvo nije poslat")));
    }

    public void acceptFriendRequest(String currentUid, String requestId, OperationCallback callback) {
        DocumentReference requestRef = db.collection(FRIEND_REQUESTS).document(requestId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot requestSnap = transaction.get(requestRef);
                    if (!requestSnap.exists()) {
                        throw abort("Zahtev nije pronadjen");
                    }
                    FriendRequest request = FriendRequest.fromSnapshot(requestSnap);
                    if (!currentUid.equals(request.recipientId)) {
                        throw abort("Zahtev nije namenjen ovom igracu");
                    }
                    if (!REQUEST_PENDING.equals(request.status)) {
                        throw abort("Zahtev vise nije aktivan");
                    }

                    DocumentReference requesterRef = db.collection(USERS).document(request.requesterId);
                    DocumentReference recipientRef = db.collection(USERS).document(request.recipientId);
                    DocumentSnapshot requester = transaction.get(requesterRef);
                    DocumentSnapshot recipient = transaction.get(recipientRef);
                    if (!requester.exists() || !recipient.exists()) {
                        throw abort("Korisnik nije pronadjen");
                    }

                    transaction.set(recipientRef.collection(FRIENDS).document(request.requesterId), buildFriendMap(requester));
                    transaction.set(requesterRef.collection(FRIENDS).document(request.recipientId), buildFriendMap(recipient));
                    transaction.update(requestRef,
                            "status", REQUEST_ACCEPTED,
                            "updatedAt", FieldValue.serverTimestamp());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Prihvatanje zahteva nije uspelo")));
    }

    public void declineFriendRequest(String currentUid, String requestId, OperationCallback callback) {
        DocumentReference requestRef = db.collection(FRIEND_REQUESTS).document(requestId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot requestSnap = transaction.get(requestRef);
                    if (!requestSnap.exists()) {
                        throw abort("Zahtev nije pronadjen");
                    }
                    FriendRequest request = FriendRequest.fromSnapshot(requestSnap);
                    if (!currentUid.equals(request.recipientId)) {
                        throw abort("Zahtev nije namenjen ovom igracu");
                    }
                    if (!REQUEST_PENDING.equals(request.status)) {
                        throw abort("Zahtev vise nije aktivan");
                    }
                    transaction.update(requestRef,
                            "status", REQUEST_DECLINED,
                            "updatedAt", FieldValue.serverTimestamp());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Odbijanje zahteva nije uspelo")));
    }

    public void removeFriend(String currentUid, String friendUid, OperationCallback callback) {
        if (currentUid == null || friendUid == null || currentUid.equals(friendUid)) {
            callback.onError("Prijatelj nije validan");
            return;
        }

        DocumentReference currentFriendRef = db.collection(USERS).document(currentUid)
                .collection(FRIENDS).document(friendUid);
        DocumentReference reverseFriendRef = db.collection(USERS).document(friendUid)
                .collection(FRIENDS).document(currentUid);
        db.runBatch(batch -> {
                    batch.delete(currentFriendRef);
                    batch.delete(reverseFriendRef);
                })
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Brisanje prijatelja nije uspelo")));
    }

    public void sendFriendRequestFromQrPayload(String currentUid, String payload, OperationCallback callback) {
        String friendUid = uidFromQrPayload(payload);
        if (friendUid == null || friendUid.trim().isEmpty()) {
            callback.onError("QR kod nije validan za Slagalicu");
            return;
        }
        sendFriendRequest(currentUid, friendUid, callback);
    }

    public String qrPayloadForUser(String uid) {
        return QR_PREFIX + uid;
    }

    private String uidFromQrPayload(String payload) {
        if (payload == null) {
            return null;
        }
        String cleaned = payload.trim();
        if (!cleaned.startsWith(QR_PREFIX)) {
            return null;
        }
        String uid = cleaned.substring(QR_PREFIX.length()).trim();
        return uid.isEmpty() ? null : uid;
    }

    private String requestId(String requesterId, String recipientId) {
        return requesterId + "_" + recipientId;
    }

    private boolean isPendingRequest(DocumentSnapshot snapshot) {
        return snapshot != null && snapshot.exists() && REQUEST_PENDING.equals(snapshot.getString("status"));
    }

    private void loadMonthlyRanks(RankCallback callback) {
        String currentMonth = currentMonthKey();
        db.collection(USERS)
                .whereEqualTo("monthlyRankMonth", currentMonth)
                .limit(500)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RankCandidate> candidates = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        candidates.add(new RankCandidate(
                                doc.getId(),
                                valueOrDefault(doc.getString("username"), "Igrac"),
                                intValue(doc.get("monthlyStars"))
                        ));
                    }

                    candidates.sort((left, right) -> {
                        int starCompare = Integer.compare(right.monthlyStars, left.monthlyStars);
                        if (starCompare != 0) {
                            return starCompare;
                        }
                        return left.username.compareToIgnoreCase(right.username);
                    });

                    Map<String, Integer> ranks = new HashMap<>();
                    int rank = 1;
                    for (RankCandidate candidate : candidates) {
                        ranks.put(candidate.uid, rank++);
                    }
                    callback.onRanks(ranks);
                })
                .addOnFailureListener(e -> callback.onError(messageOf(e, "Mesečni rang nije dostupan")));
    }

    private Map<String, Object> buildFriendMap(DocumentSnapshot userDoc) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", userDoc.getId());
        data.put("username", valueOrDefault(userDoc.getString("username"), "Igrac"));
        data.put("avatarTheme", intValue(userDoc.get("avatarTheme")));
        data.put("addedAt", FieldValue.serverTimestamp());
        return data;
    }

    private FirebaseFirestoreException abort(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.ABORTED);
    }

    private static int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value : fallback;
    }

    private static String currentMonthKey() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private String messageOf(Exception e, String fallback) {
        return e != null && e.getMessage() != null ? e.getMessage() : fallback;
    }

    public static final class FriendSummary {
        public final String uid;
        public final String username;
        public final String region;
        public final int avatarTheme;
        public final int avatarFrameRank;
        public final String avatarFrameCycleMonth;
        public final int stars;
        public final int monthlyStars;
        public final int monthlyRank;
        public final boolean isLoggedIn;
        public final String activePartyId;

        public FriendSummary(String uid, String username, String region, int avatarTheme,
                             int avatarFrameRank, String avatarFrameCycleMonth,
                             int stars, int monthlyStars, int monthlyRank,
                             boolean isLoggedIn, String activePartyId) {
            this.uid = uid;
            this.username = username;
            this.region = region;
            this.avatarTheme = avatarTheme;
            this.avatarFrameRank = avatarFrameRank;
            this.avatarFrameCycleMonth = avatarFrameCycleMonth;
            this.stars = stars;
            this.monthlyStars = monthlyStars;
            this.monthlyRank = monthlyRank;
            this.isLoggedIn = isLoggedIn;
            this.activePartyId = activePartyId;
        }

        public static FriendSummary fromUserSnapshot(DocumentSnapshot snapshot, int monthlyRank) {
            String currentMonth = currentMonthKey();
            int monthlyStars = currentMonth.equals(snapshot.getString("monthlyRankMonth"))
                    ? intValue(snapshot.get("monthlyStars"))
                    : 0;
            return new FriendSummary(
                    snapshot.getId(),
                    valueOrDefault(snapshot.getString("username"), "Igrac"),
                    valueOrDefault(snapshot.getString("region"), ""),
                    intValue(snapshot.get("avatarTheme")),
                    intValue(snapshot.get("avatarFrameRank")),
                    valueOrDefault(snapshot.getString("avatarFrameCycleMonth"), ""),
                    intValue(snapshot.get("stars")),
                    monthlyStars,
                    monthlyRank,
                    Boolean.TRUE.equals(snapshot.getBoolean("isLoggedIn")),
                    snapshot.getString("activePartyId")
            );
        }

        public boolean isAvailableForInvite() {
            return isLoggedIn && (activePartyId == null || activePartyId.trim().isEmpty());
        }

        public String leagueName() {
            return LeagueUiHelper.displayNameForStars(stars);
        }
    }

    public static final class FriendRequest {
        public final String requestId;
        public final String requesterId;
        public final String requesterUsername;
        public final String recipientId;
        public final String recipientUsername;
        public final String status;

        public FriendRequest(String requestId, String requesterId, String requesterUsername,
                             String recipientId, String recipientUsername, String status) {
            this.requestId = requestId;
            this.requesterId = requesterId;
            this.requesterUsername = requesterUsername;
            this.recipientId = recipientId;
            this.recipientUsername = recipientUsername;
            this.status = status;
        }

        public static FriendRequest fromSnapshot(DocumentSnapshot snapshot) {
            return new FriendRequest(
                    snapshot.getId(),
                    snapshot.getString("requesterId"),
                    valueOrDefault(snapshot.getString("requesterUsername"), "Igrac"),
                    snapshot.getString("recipientId"),
                    valueOrDefault(snapshot.getString("recipientUsername"), "Igrac"),
                    valueOrDefault(snapshot.getString("status"), REQUEST_PENDING)
            );
        }
    }

    private static final class RankCandidate {
        final String uid;
        final String username;
        final int monthlyStars;

        RankCandidate(String uid, String username, int monthlyStars) {
            this.uid = uid;
            this.username = username;
            this.monthlyStars = monthlyStars;
        }
    }
}
