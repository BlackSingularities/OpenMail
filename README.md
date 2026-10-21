<p align="center">
  <img src="app/src/main/res/drawable-nodpi/openmail_mark.png" width="144" alt="OpenMail" />
</p>

# OpenMail

Otwarty, rozbudowany klient poczty na Androida – wiele kont w jednej aplikacji, czarny interfejs AMOLED, bez reklam i śledzenia.

## Funkcje

**Konta**
- dowolna liczba kont: Gmail, Outlook / Hotmail, Microsoft 365, Yahoo, iCloud, WP, o2, Onet, Interia, GMX, Zoho, Fastmail, AOL, Yandex, Proton (Bridge) i każdy serwer IMAP/SMTP
- automatyczne wykrywanie serwerów (znane usługi → autokonfiguracja domeny / Thunderbird ISPDB → zgadywanie)
- logowanie hasłem / hasłem aplikacji albo przez Google i Microsoft (OAuth 2.0 + PKCE, XOAUTH2)
- hasła i tokeny szyfrowane kluczem z Android Keystore, wyłączone z kopii zapasowej
- nazwa, kolor, podpis, kolejność i konto domyślne; wybór folderów synchronizowanych w tle

**Skrzynki i lista**
- wspólne skrzynki: wszystkie odebrane, nieprzeczytane, z gwiazdką, wersje robocze, wysłane
- drzewo folderów każdego konta z licznikami (role SPECIAL-USE: Wysłane, Kosz, Spam, Archiwum…)
- filtry (nieprzeczytane, z gwiazdką, z załącznikami), grupowanie w wątki
- gesty przesunięcia w lewo/prawo (archiwizuj, usuń, przeczytane, gwiazdka, spam – do wyboru)
- zaznaczanie wielu wiadomości: przeczytane, gwiazdka, przenieś, archiwizuj, usuń
- wyszukiwanie lokalne, we wszystkich kontach albo na serwerze (IMAP SEARCH)
- widok dwupanelowy (lista + wiadomość) na tabletach i rozłożonych foldach

**Czytanie**
- HTML w bezpiecznym WebView (bez JavaScriptu, ramek i formularzy), opcjonalne przyciemnianie
- zdalne obrazy zablokowane domyślnie – „Pokaż” albo „Zawsze od tego nadawcy”
- załączniki: pobieranie na żądanie, otwieranie i udostępnianie; osadzone obrazy (cid:)
- wątek rozmowy, szczegóły nagłówków, źródło wiadomości, powiększanie tekstu

**Pisanie**
- odpowiedz, odpowiedz wszystkim, przekaż (z załącznikami), cytowanie oryginału
- adresaci jako pigułki z podpowiedziami z historii, DW/UDW, wybór konta nadawcy
- załączniki z urządzenia, wersje robocze na serwerze, podpis konta
- cofanie wysyłki (0–20 s), opcjonalne potwierdzenie
- domyślna aplikacja dla linków `mailto:` i „Udostępnij → e-mail”

**W tle**
- okresowa synchronizacja (15 min – 3 godz. albo ręcznie, opcjonalnie tylko Wi-Fi)
- powiadomienia per wiadomość z akcjami „Przeczytane” i „Archiwizuj/Usuń”
- kolejka operacji offline – zmiany wykonane bez sieci trafiają na serwer później

## Instalacja

Pobierz najnowszy plik `OpenMail-x.y.z.apk` z [Releases](https://github.com/BlackSingularities/OpenMail/releases/latest) (Android 10+).
Aplikacja sama sprawdza nowe wydania i instaluje je jednym dotknięciem.

### Gmail, Yahoo, iCloud

Te usługi wymagają **hasła aplikacji** (przy włączonej weryfikacji dwuetapowej) – kreator konta pokazuje link do odpowiedniej strony.
Konta Microsoft (Outlook, Hotmail, 365) wymagają logowania OAuth.

## Logowanie OAuth (Google / Microsoft)

Wydanie dostaje identyfikatory klientów z sekretów repozytorium (`GOOGLE_CLIENT_ID`, `MICROSOFT_CLIENT_ID`). Bez nich działa logowanie hasłem.

- **Microsoft Entra** → App registrations → nowa aplikacja (konta osobiste i organizacyjne), platforma *Mobile and desktop*, przekierowanie `com.blacksingularities.openmail://oauth`, uprawnienia `IMAP.AccessAsUser.All`, `SMTP.Send`, `offline_access`.
- **Google Cloud** → OAuth client typu *Android* (pakiet `com.blacksingularities.openmail`, SHA-1 klucza podpisu), zakres `https://mail.google.com/`.

Identyfikatory można też wpisać w aplikacji: Ustawienia → Zaawansowane.

## Budowanie

Każdy merge do `main` buduje podpisany APK w GitHub Actions i publikuje wydanie. Pull requesty są tylko kompilowane i testowane.
Wersja pochodzi z pliku `VERSION`, opis wydania z `CHANGELOG.md`.

```bash
gradle :app:assembleDebug
```

## Licencja

MIT. Biblioteki: Jakarta Mail dla Androida (EPL 2.0 / GPL 2.0 z wyjątkiem klasowym), jsoup (MIT), AndroidX (Apache 2.0).
