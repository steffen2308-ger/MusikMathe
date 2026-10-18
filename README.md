# MusikMathe

Ein eigenständiger Android-Browser als Grundlage für spätere Spezialfunktionen. Die erste Version verwendet die Chromium-WebView des Geräts. Mindestversion: Android 6; private Tabs ab Android 9. Die App heißt vorerst wie dieses Repository.

## APK installieren

Unter **Actions → Android APK → letzter erfolgreicher Lauf → Artifacts → musikmathe-debug-apk** das ZIP herunterladen, entpacken und `app-debug.apk` installieren. GitHub kann dafür eine Anmeldung verlangen. Android erlaubt die Installation nach Freigabe der verwendeten Download-/Datei-App.

Spätere Testversionen können als Update installiert werden. Dafür wird ein fester öffentlicher Entwicklungsschlüssel verwendet (`app/debug.keystore`, Alias `androiddebugkey`, Passwort `android`). Dieser Testschlüssel ist nicht für eine Store-Veröffentlichung gedacht. Paketname: `de.steffen.musikmathe`. Die Kreuzworträtsel-App ist ein getrenntes Projekt und bleibt unverändert.

## Funktionen

- Adresse oder Suchbegriff eingeben; Suchmaschinen Google, DuckDuckGo und Bing in der Adressleiste.
- Zurück, Vorwärts, Startseite, Neuladen; während des Ladens stoppt die Neuladen-Taste die Seite. Lange auf Neuladen leert den Arbeitsspeicher-Cache und lädt erneut.
- Bis zu 20 Tabs, Tabübersicht, Schließen und Wiederherstellung der zuletzt geöffneten Adressen nach einem Neustart. Lange auf die Tabtaste öffnet einen neuen Tab. Innerhalb einer Sitzung bleibt der Navigationsverlauf je Tab erhalten; nach einem vollständigen App-Neustart werden nur die letzten Adressen wiederhergestellt.
- Lesezeichen, Verlauf, Teilen, Link kopieren, Suche innerhalb einer Seite und Desktopansicht.
- Lange auf einen Weblink drücken: in neuem Tab öffnen, kopieren oder teilen.
- Datei-Downloads über Androids Downloadmanager nach Bestätigung; Downloadübersicht im Menü. Dateiuploads über die Android-Dateiauswahl, einschließlich Mehrfachauswahl, wenn die Website sie anfordert.
- JavaScript, Cookies und lokaler Websitespeicher; Zoomen und HTML-Vollbildvideos.
- Kamera, Mikrofon und Standort nur nach Website-Abfrage und Android-Berechtigung. Tabs wechseln oder navigieren beendet offene Website-Anfragen. Nicht angeforderte Berechtigungen werden nicht pauschal vergeben.
- Verlauf, Cookies/Website-Daten und Cache getrennt löschen. Lesezeichen bleiben dabei erhalten.
- Als Browser für HTTP-/HTTPS-Links in Android auswählbar. Eine Standardbrowser-Zuordnung verwaltet Android in den Systemeinstellungen.

## Grenzen der ersten Version

WebView ist keine vollständige Chrome-Distribution. Chrome-Kontosynchronisierung, Passwörter-/Passkey-Manager, Chrome-Erweiterungen, Übersetzungsdienst, Offline-Seiten sind nicht implementiert. Manche Login-Anbieter akzeptieren eingebettete WebViews nicht. DRM, Streaming, WebAuthn und einige Geräteschnittstellen hängen von Website, Android und installierter WebView-Version ab. Downloads aus `blob:`-/`data:`-Adressen und direkte Kameraaufnahme aus einem Datei-Uploadfeld sind noch nicht implementiert. Die Startseiten-Suchbox verwendet Google; die Suchmaschinenwahl gilt für die Adressleiste.

Ungültige TLS-Zertifikate werden von WebView abgelehnt; die App bietet keinen Zertifikats-Bypass. HTTPS-Seiten dürfen keine unsicheren gemischten Inhalte laden. Automatisch ausgelöste externe App-Links sind blockiert. Normale HTTP-Seiten bleiben wie in einem allgemeinen Browser erreichbar. Es gibt keine JavaScript-Schnittstelle zu Android und keine Freigabe beliebiger lokaler Dateien.

## Entwicklung und Prüfungen

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Keine Laufzeitbibliotheken außerhalb der Android-Plattform. GitHub Actions richtet SDK und Gradle ein und führt aus:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Die Tests prüfen Adress-/Sucheingaben, blockierte lokale und ausführbare URL-Schemata, Tab-Wiederherstellung, Lesezeichen, Verlauf, Einstellungen und WebView-Konfiguration. Robolectric führt keine echten Netzwerkseiten aus. Website-Logins, Videowiedergabe und die gerätespezifische WebView sollten zusätzlich auf einem echten Android-Gerät geprüft werden. Die installierte Android System WebView beziehungsweise Chrome sollte aktuell sein.

## Private Tabs und Deutschland-Startseite (0.2)

**Menü → Neuer privater Tab** öffnet den getrennten privaten Bereich. Mit **Zu normalen Tabs / Zu privaten Tabs** wechselt man zwischen den Bereichen. Ein P in der Adressleiste und eine violette Umrandung kennzeichnen private Tabs. Beim letzten privaten Tab oder **Alle privaten Tabs schließen** endet die Sitzung. Normale Tabs und Anmeldungen bleiben erhalten.

Private Tabs laufen ab Android 9 in einem nicht exportierten eigenen Prozess mit eigenem WebView-Datenverzeichnis. Vor dem ersten WebView-/CookieManager-Zugriff wird ein eventuell verbliebenes privates Profil gelöscht; schlägt dies fehl, startet der private Browser nicht. Private Adressen, Verlauf und Auswahl werden nicht gespeichert oder wiederhergestellt. Formularspeicherung und Autofill sind dort deaktiviert; Android wird um eine Tastatur ohne personalisiertes Lernen gebeten. FLAG_SECURE verhindert Screenshots der privaten Ansicht. Beim Beenden werden die WebViews zerstört, das private Verzeichnis bereinigt und der private Prozess beendet; der normale Prozess bereinigt verbliebene Dateien zusätzlich, sobald der private Prozess gestoppt ist. Bei einem erzwungenen Prozessende kann die Bereinigung erst beim nächsten Start erfolgen.

Private Tabs verbergen weder die IP-Adresse noch Aktivitäten vor Websites oder Internetanbietern. Bewusst heruntergeladene Dateien und geteilte/kopierte Inhalte bleiben erhalten. Lesezeichen sind im privaten Bereich lesbar; neue Lesezeichen werden dort nicht gespeichert. Unter Android 6–8 bleibt der normale Browser nutzbar, statt eine nur scheinbare Trennung anzubieten.

Auf der Startseite gibt es drei neue Zugänge für Deutschland:

- **Wikipedia · meistgelesen**: lokale Ansicht mit den 50 meistgelesenen Artikeln aus Deutschland (Wikimedia Analytics API, DE, alle Wikipedia-Sprachen). Das aktuelle verfügbare Tagesdatum wird angezeigt; bei fehlenden Tagesdaten werden bis zu sieben vergangene Tage geprüft. Wikipedia veröffentlicht gerundete Artikelaufrufe, keine Suchanfragen. Keine Umdeutung einer Sprachrangliste in ein Länderranking. Die Liste wird erst beim Öffnen geladen; Netzwerkfehler liefern eine Wiederholen-Ansicht, keine erfundenen Zahlen.
- **Meistbesuchte Webseiten**: [Similarweb Deutschland-Ranking](https://www.similarweb.com/top-websites/germany/).
- **Google Trends**: [Aktuelle Suchtrends für Deutschland, deutsche Oberfläche](https://trends.google.com/trending?geo=DE&hl=de).

Die Wikipedia-Ansicht basiert auf dem offiziellen [Country-Ranking-Endpunkt](https://doc.wikimedia.org/generated-data-platform/aqs/analytics-api/reference/page-views.html). Sie verwendet weder Standortfreigabe noch Browsercookies.
