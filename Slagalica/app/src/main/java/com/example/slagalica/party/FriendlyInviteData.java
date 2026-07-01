package com.example.slagalica.party;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class FriendlyInviteData {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_EXPIRED = "expired";

    public String inviteId;
    public String inviterId;
    public String inviterUsername;
    public String inviteeId;
    public String inviteeUsername;
    public String status;
    public String partyId;
    public Timestamp expiresAt;

    public static FriendlyInviteData fromSnapshot(DocumentSnapshot snapshot) {
        FriendlyInviteData data = new FriendlyInviteData();
        data.inviteId = snapshot.getId();
        data.inviterId = snapshot.getString("inviterId");
        data.inviterUsername = snapshot.getString("inviterUsername");
        data.inviteeId = snapshot.getString("inviteeId");
        data.inviteeUsername = snapshot.getString("inviteeUsername");
        data.status = snapshot.getString("status");
        data.partyId = snapshot.getString("partyId");
        data.expiresAt = snapshot.getTimestamp("expiresAt");
        return data;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.toDate().getTime() <= System.currentTimeMillis();
    }
}
