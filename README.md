# 📱 Mobilne aplikacije — Tim 30 — Slagalica

Mobilna aplikacija po ugledu na kviz Slagalica: takmičenje dva igrača kroz šest igara, profili sa statistikom, lige, rang liste, prijatelji, čet po regionima, izazovi i turniri.

## 👥 Članovi tima

- RA 209/2022 - Strahinja Ponjević
- RA 226/2022 - Stefan Onjin
- RA 126/2022 - Filip Čonić

## Korisnici (po specifikaciji)

- **Registrovan igrač**: sve funkcije — regularne i prijateljske partije, profil sa statistikom, zvezde, tokeni, lige, rang liste, prijatelji, čet regiona, izazovi, notifikacije.
- **Neregistrovan igrač (gost)**: samo igranje protiv drugog igrača, kroz sesiju sa kodom (Igraj kao gost → Kreiraj igru → podeli kod → protivnik unese kod). Gostinske partije ne troše tokene, ne nose zvezde i ne ulaze u statistiku.

## Partija i igre

Regularna partija se sastoji od šest igara u zadatom redosledu; jedan token = jedna partija; pobednik dobija 10 zvezda + 1 zvezdu na svakih 40 bodova, gubitnik gubi 10 zvezda uz istu bonus formulu. Ukupan skor partije se prikazuje u zaglavlju svake igre pored skora tekuće igre.

| Igra | Pravila (sažeto) |
|---|---|
| Ko zna zna | 5 pitanja × 4 odgovora, 5s po pitanju, +10 tačno / −5 netačno, brži igrač nosi poene |
| Spojnice | 2 runde × 30s, povezivanje 5 parova, 2 boda po paru, drugi igrač dobija nepovezane |
| Asocijacije | 2 runde × 2 min, naizmenično otvaranje polja i pogađanje; kolona = 2 + neotvorena polja; konačno = 7 + 6 po zatvorenoj koloni + delimične kolone |
| Skočko | 2 runde × 30s, 6 pokušaja; pogodak u 1–2 → 20, 3–4 → 15, 5–6 → 10; ako aktivni ne pogodi, protivnik ima 1 pokušaj / 10s / 10 bodova |
| Korak po korak | 2 runde, 7 koraka × 10s, 20 bodova sa −2 po koraku; protivnik 5 bodova ako preuzme |
| Moj broj | 2 runde × 1 min, 6 brojeva + operacije do traženog broja, 10 bodova; stop i shake senzorom |

## Arhitektura (troslojna)

- **Prezentacioni sloj**: Activity klase + XML layouti (`MainActivity`, ekrani igara, `PartyActivity`, `NotificationsActivity`, ...). Jedinstvena svetla Material3 tema sa primarnom bojom `#6200EE`.
- **Poslovna logika**: repository klase (`PartyRepository`, `AsocijacijeSessionRepository`, `SkockoSessionRepository`, `SpojniceSessionRepository`, `ChallengeRepository`, `ProfileStatsUpdater`, `NotificationStore`, ...) — sva pravila igara, matchmaking, bodovanje, nagrade.
- **Sloj podataka**: Firebase Authentication (email/lozinka + anonimni gosti) i Cloud Firestore.

## Kako radi igra dva igrača u realnom vremenu

1. Matchmaking (`matchmaking_queue`) ili poziv prijatelju kreira dokument `parties/{partyId}` i prateći `sessions/{partyId}` sa ID-jevima i imenima igrača.
2. `PartyActivity` sluša party dokument i kod OBA igrača otvara Activity tekuće igre.
3. Svaka igra drži živo stanje u `games/{partyId}_{gameKey}` (faze, potezi, skorovi, `phaseStartedAt` server timestamp za tajmere). Vlasnik inicijalizuje dokument; oba klijenta imaju snapshot listener; piše samo igrač čiji je potez.
4. Na kraju igre vlasnik poziva `finishGameAndAdvance` koji upisuje skorove u partiju i prebacuje na sledeću igru; posle šeste se dodeljuju zvezde/tokeni.
5. Napuštanje partije traži potvrdu; napuštanjem igrač gubi partiju, protivnik nastavlja bez čekanja (tuđi potezi se preskaču).

Gostinska sesija sa kodom koristi isti mehanizam (`sessions` + `games`), samo bez party dokumenta, tokena, zvezda i statistike.

## Notifikacije

Aplikacija ne koristi FCM — notifikacije generišu Firestore listeneri dok je aplikacija živa i korisnik ulogovan. Kanali po specifikaciji: čet, rangiranje, nagrade, ostalo. Svaka notifikacija se upisuje u `users/{uid}/notifications` i vidi se na stranici **Notifikacije** (istorija, filter pročitane/nepročitane, označavanje pročitanim, tap vodi na odgovarajući ekran). Na Androidu 13+ potrebno je prihvatiti sistemsku dozvolu za notifikacije pri prvom pokretanju.

## Firestore kolekcije

`users` (+ podkolekcija `notifications`), `parties`, `sessions`, `games`, `matchmaking_queue`, `friends`, `friend_requests`, `friendly_invites`, `challenges`, `region_chats/{region}/messages`, `region_stats`, `region_cycles`, i sadržaj igara: `asocijacije_sets`, `spojnice_sets`, `ko_zna_zna_questions`, `korak_po_korak_questions`.

Sadržaj igara se dodaje direktno u Firestore konzoli. Format `asocijacije_sets` dokumenta: nizovi `columnA`–`columnD` (po 4 stringa), stringovi `solutionA`–`solutionD` i `finalSolution`; potrebno je najmanje 2 dokumenta (igra bira 2 nasumična po partiji).

## Pokretanje

1. Podešavanje okruženja

Za uspešno pokretanje projekta koristi se Android Studio. Pre pokretanja, proveriti da li su instalirani sledeći paketi:

 - Android SDK Build-Tools
 - Android SDK Command-line Tools
 - Android SDK platform-tools
 - Android Emulator

2. Firebase

Projekat očekuje `google-services.json` u `Slagalica/app/`. U Firebase konzoli moraju biti uključeni Authentication (Email/Password i Anonymous) i Cloud Firestore, sa objavljenim pravilima koja dozvoljavaju ulogovanim korisnicima pristup kolekcijama navedenim iznad.

3. Otvaranje projekta

Za importovanje postojećeg projekta izabrati iz menija File > New > Import Project. Za kloniranje projekta sa Git-a, odaberite File > New > Project from Version Control i unesite URL do repozitorijuma.

4. Pokretanje aplikacije

4.1 Fizički uređaj: omogućiti developer options (Settings > About Phone > Build Number ×7), uključiti USB debugging, povezati uređaj i kliknuti Run.

4.2 Virtuelni uređaj: Tools > Device Manager > Create Device, izabrati profil i system image, pa Run.

5. Testiranje dva igrača

Pokrenuti aplikaciju na dva emulatora/uređaja sa dva različita registrovana naloga (za regularne partije, čet i notifikacije) ili jedan registrovan + gost kroz sesiju sa kodom. Za test notifikacija oba naloga moraju biti u istom regionu, a primaočeva aplikacija pokrenuta.

## Raspodela funkcionalnosti

| | KT1 + KT2 | KO |
|---|---|---|
| Student 1 | Registracija i logovanje; Korak po korak; Moj broj | Igranje partija; Čet; Izazov |
| Student 2 | Prikaz profila; Ko zna zna; Spojnice | Prikaz regiona; Lige; Prijatelji |
| Student 3 | Notifikacije; Asocijacije; Skočko | Rang lista; Turnir; Dnevne misije |
