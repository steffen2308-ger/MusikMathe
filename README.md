# MusikMathe

Ein eigenständiger Android-Browser als Grundlage für spätere Spezialfunktionen. Die erste Version verwendet die Chromium-WebView des Geräts. Mindestversion: Android 6. Die App heißt vorerst wie dieses Repository.

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

WebView ist keine vollständige Chrome-Distribution. Chrome-Kontosynchronisierung, Passwörter-/Passkey-Manager, Chrome-Erweiterungen, Übersetzungsdienst, isolierte private Tabs und Offline-Seiten sind nicht implementiert. Manche Login-Anbieter akzeptieren eingebettete WebViews nicht. DRM, Streaming, WebAuthn und einige Geräteschnittstellen hängen von Website, Android und installierter WebView-Version ab. Downloads aus `blob:`-/`data:`-Adressen und direkte Kameraaufnahme aus einem Datei-Uploadfeld sind noch nicht implementiert. Die Startseiten-Suchbox verwendet Google; die Suchmaschinenwahl gilt für die Adressleiste.

Ungültige TLS-Zertifikate werden von WebView abgelehnt; die App bietet keinen Zertifikats-Bypass. HTTPS-Seiten dürfen keine unsicheren gemischten Inhalte laden. Automatisch ausgelöste externe App-Links sind blockiert. Normale HTTP-Seiten bleiben wie in einem allgemeinen Browser erreichbar. Es gibt keine JavaScript-Schnittstelle zu Android und keine Freigabe beliebiger lokaler Dateien.

## Entwicklung und Prüfungen

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Keine Laufzeitbibliotheken außerhalb der Android-Plattform. GitHub Actions richtet SDK und Gradle ein und führt aus:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Die Tests prüfen Adress-/Sucheingaben, blockierte lokale und ausführbare URL-Schemata, Tab-Wiederherstellung, Lesezeichen, Verlauf, Einstellungen und WebView-Konfiguration. Robolectric führt keine echten Netzwerkseiten aus. Website-Logins, Videowiedergabe und die gerätespezifische WebView sollten zusätzlich auf einem echten Android-Gerät geprüft werden. Die installierte Android System WebView beziehungsweise Chrome sollte aktuell sein.
