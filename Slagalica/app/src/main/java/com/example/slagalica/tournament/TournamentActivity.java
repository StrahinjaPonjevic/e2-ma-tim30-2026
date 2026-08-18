package com.example.slagalica.tournament;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.leagues.LeagueUiHelper;
import com.example.slagalica.party.PartyActivity;
import com.example.slagalica.party.PartyData;
import com.example.slagalica.party.PartyRepository;
import com.example.slagalica.profile.UserProfile;
import com.example.slagalica.profile.UserProfileRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentActivity extends AppCompatActivity {

    private static final int[] AVATAR_COLORS = {
            Color.rgb(201, 134, 82),
            Color.rgb(61, 126, 170),
            Color.rgb(109, 76, 156),
            Color.rgb(84, 126, 91),
            Color.rgb(198, 73, 91),
            Color.rgb(73, 124, 199)
    };

    private TextView tvTournamentStatus, tvBracket, tvTournamentResult;
    private TextView[] slotAvatars = new TextView[4];
    private TextView[] slotNames = new TextView[4];
    private TextView[] slotLeagues = new TextView[4];
    private Button btnJoinTournament, btnPlayMyParty, btnLeaveTournament, btnTournamentBack;

    private TournamentRepository tournamentRepository;
    private PartyRepository partyRepository;
    private UserProfileRepository profileRepository;

    private String currentUserId;
    private UserProfile myProfile;
    private String tournamentId;
    private TournamentData currentTournament;
    private ListenerRegistration tournamentListener;
    private final Map<String, ListenerRegistration> partyListeners = new HashMap<>();
    private boolean resultAnimationShown = false;
    private boolean progressRequestRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(this, "Morate biti prijavljeni za turnir.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        tournamentRepository = new TournamentRepository();
        partyRepository = new PartyRepository();
        profileRepository = new UserProfileRepository();

        bindViews();
        loadProfileThenFindTournament();
    }

    private void bindViews() {
        tvTournamentStatus = findViewById(R.id.tvTournamentStatus);
        tvBracket = findViewById(R.id.tvBracket);
        tvTournamentResult = findViewById(R.id.tvTournamentResult);
        slotAvatars[0] = findViewById(R.id.tvSlot1Avatar);
        slotAvatars[1] = findViewById(R.id.tvSlot2Avatar);
        slotAvatars[2] = findViewById(R.id.tvSlot3Avatar);
        slotAvatars[3] = findViewById(R.id.tvSlot4Avatar);
        slotNames[0] = findViewById(R.id.tvSlot1Name);
        slotNames[1] = findViewById(R.id.tvSlot2Name);
        slotNames[2] = findViewById(R.id.tvSlot3Name);
        slotNames[3] = findViewById(R.id.tvSlot4Name);
        slotLeagues[0] = findViewById(R.id.tvSlot1League);
        slotLeagues[1] = findViewById(R.id.tvSlot2League);
        slotLeagues[2] = findViewById(R.id.tvSlot3League);
        slotLeagues[3] = findViewById(R.id.tvSlot4League);
        btnJoinTournament = findViewById(R.id.btnJoinTournament);
        btnPlayMyParty = findViewById(R.id.btnPlayMyParty);
        btnLeaveTournament = findViewById(R.id.btnLeaveTournament);
        btnTournamentBack = findViewById(R.id.btnTournamentBack);

        btnJoinTournament.setOnClickListener(v -> confirmJoin());
        btnPlayMyParty.setOnClickListener(v -> openMyParty());
        btnLeaveTournament.setOnClickListener(v -> leaveTournament());
        btnTournamentBack.setOnClickListener(v -> finish());
    }

    private void loadProfileThenFindTournament() {
        tvTournamentStatus.setText("Ucitavanje profila...");
        profileRepository.loadProfile(currentUserId, new UserProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                myProfile = profile;
                runOnUiThread(() -> findMyTournament());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(TournamentActivity.this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void findMyTournament() {
        tvTournamentStatus.setText("Provera aktivnih turnira...");
        tournamentRepository.findMyActiveTournament(currentUserId, new TournamentRepository.FindCallback() {
            @Override
            public void onFound(String foundTournamentId) {
                runOnUiThread(() -> attachTournament(foundTournamentId));
            }

            @Override
            public void onNotFound() {
                runOnUiThread(() -> showJoinState());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(TournamentActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showJoinState() {
        currentTournament = null;
        tvTournamentStatus.setText("Niste prijavljeni ni na jedan turnir.");
        tvBracket.setText("Turnir zahteva 4 igraca: dve polufinalne partije, pa finale.\nUcesce kosta "
                + TournamentRepository.ENTRY_COST + " tokena.");
        tvTournamentResult.setVisibility(View.GONE);
        btnJoinTournament.setVisibility(View.VISIBLE);
        btnPlayMyParty.setVisibility(View.GONE);
        btnLeaveTournament.setVisibility(View.GONE);
        for (int i = 0; i < 4; i++) {
            renderEmptySlot(i);
        }
    }

    private void confirmJoin() {
        new AlertDialog.Builder(this)
                .setTitle("Prijava na turnir")
                .setMessage("Ucesce kosta " + TournamentRepository.ENTRY_COST
                        + " tokena. Odustajanjem u bilo kojoj fazi ne dobijate tokene nazad. Nastaviti?")
                .setPositiveButton("Prijavi se", (dialog, which) -> joinTournament())
                .setNegativeButton("Odustani", null)
                .show();
    }

    private void joinTournament() {
        btnJoinTournament.setEnabled(false);
        tournamentRepository.joinOpenTournament(currentUserId, myProfile.username, myProfile.stars,
                myProfile.avatarTheme, new TournamentRepository.TournamentCallback() {
                    @Override
                    public void onSuccess(String joinedTournamentId) {
                        runOnUiThread(() -> {
                            btnJoinTournament.setEnabled(true);
                            attachTournament(joinedTournamentId);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnJoinTournament.setEnabled(true);
                            Toast.makeText(TournamentActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void leaveTournament() {
        if (tournamentId == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Napustiti turnir?")
                .setMessage("Ulozeni tokeni se ne vracaju.")
                .setPositiveButton("Napusti", (dialog, which) ->
                        tournamentRepository.leaveWhileWaiting(tournamentId, currentUserId,
                                new TournamentRepository.TournamentCallback() {
                                    @Override
                                    public void onSuccess(String id) {
                                        runOnUiThread(() -> {
                                            detachAll();
                                            tournamentId = null;
                                            showJoinState();
                                        });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> Toast.makeText(TournamentActivity.this,
                                                message, Toast.LENGTH_SHORT).show());
                                    }
                                }))
                .setNegativeButton("Ostani", null)
                .show();
    }

    private void attachTournament(String id) {
        detachAll();
        tournamentId = id;
        resultAnimationShown = false;
        btnJoinTournament.setVisibility(View.GONE);
        tournamentListener = tournamentRepository.listenTournament(id,
                new TournamentRepository.TournamentListener() {
                    @Override
                    public void onChanged(TournamentData tournament) {
                        runOnUiThread(() -> {
                            currentTournament = tournament;
                            renderTournament(tournament);
                            attachPartyListeners(tournament);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(TournamentActivity.this,
                                message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void attachPartyListeners(TournamentData tournament) {
        attachPartyListener(tournament.semi1PartyId);
        attachPartyListener(tournament.semi2PartyId);
        attachPartyListener(tournament.finalPartyId);
    }

    private void attachPartyListener(String partyId) {
        if (partyId == null || partyListeners.containsKey(partyId)) {
            return;
        }
        ListenerRegistration registration = partyRepository.listenParty(partyId,
                new PartyRepository.PartyListener() {
                    @Override
                    public void onPartyChanged(PartyData party) {
                        boolean done = PartyData.STATUS_FINISHED.equals(party.status)
                                || PartyData.STATUS_FORFEITED.equals(party.status);
                        if (done) {
                            runOnUiThread(() -> requestProgress());
                        }
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
        partyListeners.put(partyId, registration);
    }

    private void requestProgress() {
        if (tournamentId == null || progressRequestRunning) {
            return;
        }
        progressRequestRunning = true;
        tournamentRepository.recordProgressIfNeeded(tournamentId,
                new TournamentRepository.ProgressCallback() {
                    @Override
                    public void onProgressed(List<String> newWinnerIds, String finalWinnerId) {
                        progressRequestRunning = false;
                    }

                    @Override
                    public void onError(String message) {
                        progressRequestRunning = false;
                    }
                });
    }

    private void renderTournament(TournamentData tournament) {
        for (int i = 0; i < 4; i++) {
            if (i < tournament.players.size()) {
                renderPlayerSlot(i, tournament.players.get(i));
            } else {
                renderEmptySlot(i);
            }
        }

        btnLeaveTournament.setVisibility(
                TournamentData.STATUS_WAITING.equals(tournament.status) ? View.VISIBLE : View.GONE);

        String myPartyId = resolveMyOpenPartyId(tournament);
        btnPlayMyParty.setVisibility(myPartyId != null ? View.VISIBLE : View.GONE);

        switch (tournament.status) {
            case TournamentData.STATUS_WAITING:
                tvTournamentStatus.setText("Cekanje igraca: " + tournament.players.size() + "/4");
                tvBracket.setText("Turnir pocinje automatski kada se prijave 4 igraca.");
                break;
            case TournamentData.STATUS_SEMIFINALS:
                tvTournamentStatus.setText("Polufinala su u toku!");
                tvBracket.setText(buildBracketText(tournament));
                break;
            case TournamentData.STATUS_FINAL:
                tvTournamentStatus.setText("Finale je u toku!");
                tvBracket.setText(buildBracketText(tournament));
                break;
            case TournamentData.STATUS_FINISHED:
                tvTournamentStatus.setText("Turnir je zavrsen.");
                tvBracket.setText(buildBracketText(tournament));
                showFinalResult(tournament);
                break;
            default:
                tvTournamentStatus.setText("");
                break;
        }
    }

    private String resolveMyOpenPartyId(TournamentData tournament) {
        if (TournamentData.STATUS_SEMIFINALS.equals(tournament.status)) {
            int myIndex = tournament.playerIds.indexOf(currentUserId);
            if (myIndex == 0 || myIndex == 1) {
                return tournament.semi1WinnerId == null ? tournament.semi1PartyId : null;
            }
            if (myIndex == 2 || myIndex == 3) {
                return tournament.semi2WinnerId == null ? tournament.semi2PartyId : null;
            }
            return null;
        }
        if (TournamentData.STATUS_FINAL.equals(tournament.status)
                && (currentUserId.equals(tournament.semi1WinnerId)
                || currentUserId.equals(tournament.semi2WinnerId))
                && tournament.finalWinnerId == null) {
            return tournament.finalPartyId;
        }
        return null;
    }

    private void openMyParty() {
        TournamentData tournament = currentTournament;
        if (tournament == null) {
            return;
        }
        String partyId = resolveMyOpenPartyId(tournament);
        if (partyId == null) {
            return;
        }
        Intent intent = new Intent(this, PartyActivity.class);
        intent.putExtra(PartyActivity.EXTRA_PARTY_ID, partyId);
        startActivity(intent);
    }

    private String buildBracketText(TournamentData tournament) {
        StringBuilder builder = new StringBuilder();
        if (tournament.players.size() >= 4) {
            builder.append("Polufinale 1: ")
                    .append(tournament.playerUsername(tournament.playerIds.get(0)))
                    .append(" vs ")
                    .append(tournament.playerUsername(tournament.playerIds.get(1)));
            if (tournament.semi1WinnerId != null) {
                builder.append("  \u2192  ").append(tournament.playerUsername(tournament.semi1WinnerId));
            }
            builder.append("\nPolufinale 2: ")
                    .append(tournament.playerUsername(tournament.playerIds.get(2)))
                    .append(" vs ")
                    .append(tournament.playerUsername(tournament.playerIds.get(3)));
            if (tournament.semi2WinnerId != null) {
                builder.append("  \u2192  ").append(tournament.playerUsername(tournament.semi2WinnerId));
            }
            if (tournament.semi1WinnerId != null && tournament.semi2WinnerId != null) {
                builder.append("\nFinale: ")
                        .append(tournament.playerUsername(tournament.semi1WinnerId))
                        .append(" vs ")
                        .append(tournament.playerUsername(tournament.semi2WinnerId));
                if (tournament.finalWinnerId != null) {
                    builder.append("\n\ud83c\udfc6 Pobednik: ")
                            .append(tournament.playerUsername(tournament.finalWinnerId));
                }
            }
        }
        return builder.toString();
    }

    private void showFinalResult(TournamentData tournament) {
        if (resultAnimationShown || tournament.finalWinnerId == null) {
            return;
        }
        resultAnimationShown = true;
        tvTournamentResult.setVisibility(View.VISIBLE);

        boolean iWon = currentUserId.equals(tournament.finalWinnerId);
        boolean iWasFinalist = currentUserId.equals(tournament.semi1WinnerId)
                || currentUserId.equals(tournament.semi2WinnerId);

        if (iWon) {
            tvTournamentResult.setText("\ud83c\udfc6\nPOBEDILI STE TURNIR!\n+3 tokena i +10 zvezda");
            tvTournamentResult.setTextColor(Color.parseColor("#2E7D32"));
        } else if (iWasFinalist) {
            tvTournamentResult.setText("\ud83d\ude1e\nIzgubili ste finale.");
            tvTournamentResult.setTextColor(Color.parseColor("#C62828"));
        } else {
            tvTournamentResult.setText("Turnir je osvojio: "
                    + tournament.playerUsername(tournament.finalWinnerId));
            tvTournamentResult.setTextColor(Color.parseColor("#333333"));
        }

        tvTournamentResult.setScaleX(0f);
        tvTournamentResult.setScaleY(0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvTournamentResult, "scaleX", 0f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvTournamentResult, "scaleY", 0f, 1.2f, 1f);
        scaleX.setDuration(800);
        scaleY.setDuration(800);
        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());
        scaleX.start();
        scaleY.start();

        if (iWon) {
            ObjectAnimator wobble = ObjectAnimator.ofFloat(tvTournamentResult, "rotation", -4f, 4f);
            wobble.setDuration(600);
            wobble.setRepeatCount(ValueAnimator.INFINITE);
            wobble.setRepeatMode(ValueAnimator.REVERSE);
            wobble.setStartDelay(800);
            wobble.start();
        }
    }

    private void renderPlayerSlot(int index, Map<String, Object> player) {
        String username = player.get("username") instanceof String ? (String) player.get("username") : "Igrac";
        int stars = player.get("stars") instanceof Number ? ((Number) player.get("stars")).intValue() : 0;
        int avatarTheme = player.get("avatarTheme") instanceof Number
                ? ((Number) player.get("avatarTheme")).intValue() : 0;
        int safeIndex = Math.max(0, Math.min(avatarTheme, AVATAR_COLORS.length - 1));

        slotAvatars[index].setText(extractInitials(username));
        slotAvatars[index].setBackgroundColor(AVATAR_COLORS[safeIndex]);
        slotAvatars[index].setTextColor(Color.WHITE);
        slotNames[index].setText(username);
        slotLeagues[index].setText(LeagueUiHelper.displayNameForStars(stars));
    }

    private void renderEmptySlot(int index) {
        slotAvatars[index].setText("?");
        slotAvatars[index].setBackgroundColor(Color.parseColor("#BDBDBD"));
        slotAvatars[index].setTextColor(Color.WHITE);
        slotNames[index].setText("Slobodno mesto");
        slotLeagues[index].setText("");
    }

    private String extractInitials(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "IG";
        }
        String cleaned = username.trim();
        return cleaned.length() == 1
                ? cleaned.toUpperCase()
                : cleaned.substring(0, 2).toUpperCase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tournamentId != null) {
            requestProgress();
        }
    }

    private void detachAll() {
        if (tournamentListener != null) {
            tournamentListener.remove();
            tournamentListener = null;
        }
        for (ListenerRegistration registration : partyListeners.values()) {
            registration.remove();
        }
        partyListeners.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachAll();
    }
}
