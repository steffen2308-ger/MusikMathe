# BestBrowser

Ein eigenständiger Android-Browser als Grundlage für spätere Spezialfunktionen. Die erste Version verwendet die Chromium-WebView des Geräts. Mindestversion: Android 6; private Tabs ab Android 9. Die App heißt vorerst wie dieses Repository.

## APK installieren

Unter **Actions → Android APK → letzter erfolgreicher Lauf → Artifacts → bestbrowser-debug-apk** das ZIP herunterladen, entpacken und `app-debug.apk` installieren. GitHub kann dafür eine Anmeldung verlangen. Android erlaubt die Installation nach Freigabe der verwendeten Download-/Datei-App.

Spätere Testversionen können als Update installiert werden. Dafür wird ein fester öffentlicher Entwicklungsschlüssel verwendet (`app/debug.keystore`, Alias `androiddebugkey`, Passwort `android`). Dieser Testschlüssel ist nicht für eine Store-Veröffentlichung gedacht. Paketname: `de.steffen.musikmathe`. Die Kreuzworträtsel-App ist ein getrenntes Projekt und bleibt unverändert.

## Funktionen

- Adresse oder Suchbegriff eingeben; passende Vorschläge aus dem Verlauf nach Webadresse und Seitentitel. Antippen öffnet die Seite direkt. Suchmaschinen Google, DuckDuckGo und Bing in der Adressleiste.
- Zurück, Vorwärts, Startseite, Neuladen; während des Ladens stoppt die Neuladen-Taste die Seite. Lange auf Neuladen leert den Arbeitsspeicher-Cache und lädt erneut.
- Bis zu 20 Tabs, Tabübersicht, Schließen und Wiederherstellung der zuletzt geöffneten Adressen nach einem Neustart. Lange auf die Tabtaste öffnet einen neuen Tab. Innerhalb einer Sitzung bleibt der Navigationsverlauf je Tab erhalten; nach einem vollständigen App-Neustart werden nur die letzten Adressen wiederhergestellt.
- Lesezeichen, Verlauf, Teilen, Link kopieren, Suche innerhalb einer Seite und Desktopansicht.
- Geeignete Seitenlinks im WLAN vorladen; unter Einstellungen abschaltbar. Startseiten-Kacheln für Google, Yandex, SearXNG, Brave Search und Mojeek.
- Lange auf einen Weblink drücken: in neuem Tab öffnen, kopieren oder teilen.
- Datei-Downloads über Androids Downloadmanager nach Bestätigung; Downloadübersicht im Menü. Dateiuploads über die Android-Dateiauswahl, einschließlich Mehrfachauswahl, wenn die Website sie anfordert.
- JavaScript, Cookies und lokaler Websitespeicher; Zoomen und HTML-Vollbildvideos.
- Kamera, Mikrofon und Standort nur nach Website-Abfrage und Android-Berechtigung. Tabs wechseln oder navigieren beendet offene Website-Anfragen. Nicht angeforderte Berechtigungen werden nicht pauschal vergeben.
- Verlauf, Cookies/Website-Daten und Cache getrennt löschen. Lesezeichen bleiben dabei erhalten.
- Als Browser für HTTP-/HTTPS-Links in Android auswählbar. Eine Standardbrowser-Zuordnung verwaltet Android in den Systemeinstellungen.

## Grenzen der ersten Version

WebView ist keine vollständige Chrome-Distribution. Chrome-Kontosynchronisierung, Passwörter-/Passkey-Manager, Chrome-Erweiterungen, Übersetzungsdienst, Offline-Seiten sind nicht implementiert. Manche Login-Anbieter akzeptieren eingebettete WebViews nicht. DRM, Streaming, WebAuthn und einige Geräteschnittstellen hängen von Website, Android und installierter WebView-Version ab. Downloads aus `blob:`-/`data:`-Adressen und direkte Kameraaufnahme aus einem Datei-Uploadfeld sind noch nicht implementiert. Die Startseiten-Suchbox verwendet Google; die Suchmaschinenwahl gilt für die Adressleiste.

Ungültige TLS-Zertifikate werden von WebView abgelehnt; die App bietet keinen Zertifikats-Bypass. HTTPS-Seiten dürfen keine unsicheren gemischten Inhalte laden. Automatisch ausgelöste externe App-Links sind blockiert. Normale HTTP-Seiten bleiben wie in einem allgemeinen Browser erreichbar. Es gibt keine Freigabe beliebiger lokaler Dateien. Die begrenzten WebMessage-Empfänger für YouTube-Metadaten und Login-Vorschläge bieten keine allgemeinen Datei- oder Netzwerkaktionen.

## Entwicklung und Prüfungen

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Für Videoexporte wird AndroidX Media3 1.8.0 (Transformer, HLS und DASH) verwendet. GitHub Actions richtet SDK und Gradle ein und führt aus:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Die Tests prüfen Adress-/Sucheingaben, blockierte lokale und ausführbare URL-Schemata, Tab-Wiederherstellung, Lesezeichen, Verlauf, Einstellungen und WebView-Konfiguration. Robolectric führt keine echten Netzwerkseiten aus. Website-Logins, Videowiedergabe und die gerätespezifische WebView sollten zusätzlich auf einem echten Android-Gerät geprüft werden. Die installierte Android System WebView beziehungsweise Chrome sollte aktuell sein.

## Private Tabs und Deutschland-Startseite (0.2)

**Menü → Neuer privater Tab** beziehungsweise **Zu privaten Tabs** öffnet ab Version 0.4.19 eine Passwortabfrage. Jedes Passwort gehört zu einem eigenen gespeicherten privaten Bereich. Ein P in der Adressleiste und eine violette Umrandung kennzeichnen private Tabs. **Privaten Bereich sperren** und **Zu normalen Tabs** sichern den Bereich und schließen seine Ansicht. **Alle privaten Tabs schließen** entfernt seine offenen Tabs; sein Verlauf und seine Website-Daten bleiben gespeichert. Normale Tabs und Anmeldungen bleiben erhalten.

Private Bereiche laufen ab Android 9 in einem nicht exportierten eigenen Prozess mit getrenntem WebView-Arbeitsverzeichnis. Erst nach der Passwortprüfung werden die zugehörigen Website-Daten wiederhergestellt und WebView initialisiert. Verlauf, Tabs, Lesezeichen und Einstellungen liegen verschlüsselt je Passwort vor. Beim Schließen beendet die App den privaten Prozess; der normale Prozess sichert anschließend die geschlossenen Website-Datenbanken verschlüsselt und entfernt den Arbeitsbereich. Nach einem erzwungenen Prozessende erfolgt diese Sicherung beim nächsten Start. Eine kurzlebige, mit Android Keystore geschützte Wiederherstellungsdatei ermöglicht dies ohne erneute Passworteingabe; sie und ihr Keystore-Schlüssel werden nach der Sicherung gelöscht. Formularspeicherung, der normale Passwortmanager und Android-Autofill bleiben im privaten Bereich deaktiviert. FLAG_SECURE verhindert Screenshots der privaten Ansicht und der Passwortabfrage.

Private Tabs verbergen weder die IP-Adresse noch Aktivitäten vor Websites oder Internetanbietern. Bewusst heruntergeladene Dateien und geteilte/kopierte Inhalte bleiben erhalten. Jeder private Bereich hat eigene Lesezeichen und einen eigenen Videoordner; der normale Verlauf und normale Lesezeichen werden dort nicht eingeblendet. Unter Android 6–8 bleibt der normale Browser nutzbar, statt eine nur scheinbare Trennung anzubieten.

Auf der Startseite gibt es drei neue Zugänge für Deutschland:

- **Wikipedia · meistgelesen**: lokale Ansicht mit den 50 meistgelesenen Artikeln aus Deutschland (Wikimedia Analytics API, DE, alle Wikipedia-Sprachen). Das aktuelle verfügbare Tagesdatum wird angezeigt; bei fehlenden Tagesdaten werden bis zu sieben vergangene Tage geprüft. Wikipedia veröffentlicht gerundete Artikelaufrufe, keine Suchanfragen. Keine Umdeutung einer Sprachrangliste in ein Länderranking. Die Liste wird erst beim Öffnen geladen; Netzwerkfehler liefern eine Wiederholen-Ansicht, keine erfundenen Zahlen.
- **Meistbesuchte Webseiten**: [Öffentliche Semrush-Rangliste für Deutschland](https://www.semrush.com/trending-websites/de/all/) ohne Anmeldung (die führenden 20 Webseiten sind frei sichtbar).
- **Google Trends**: [Aktuelle Suchtrends für Deutschland, deutsche Oberfläche](https://trends.google.com/trending?geo=DE&hl=de).

Die Wikipedia-Ansicht basiert auf dem offiziellen [Country-Ranking-Endpunkt](https://doc.wikimedia.org/generated-data-platform/aqs/analytics-api/reference/page-views.html). Sie verwendet weder Standortfreigabe noch Browsercookies.

Zusätzlich führt GitHub Actions einen Android-9-Emulator-Test mit echter WebView aus. Ein lokaler HTTP-Testserver setzt unterschiedliche Cookies und localStorage-Werte in normalen und privaten Tabs. Der Test prüft zwei passwortgeschützte Bereiche, getrennte Cookies und Website-Speicher, eigene Verläufe, Wiederöffnung und Wiederherstellung nach gezieltem Prozessabbruch; er benötigt keine externe Website.

## Videos erkennen und speichern (0.3)

Video auf einer Webseite starten, dann die **Videotaste** in der unteren Leiste oder **Menü → Videos erkennen und speichern** öffnen. Die Zahl an der Taste zeigt gefundene Quellen des aktuellen Tabs. Die Liste sammelt direkte MP4-/WebM-/andere Videoadressen, HLS-Manifeste (`m3u8`) und DASH-Manifeste (`mpd`) aus WebView-Anfragen, HTML5-Videoelementen und den Ressourcen der Seite. Nach dem Laden der Seite beobachtet ein seiteneigenes JavaScript außerdem Content-Type-Header nachfolgender fetch-/XHR-Antworten, ohne deren Inhalt zu lesen oder eine JavaScript-Schnittstelle zu Android freizugeben. So werden auch manche Quellen ohne Video-Dateiendung gefunden. Gleichnamige Adressen werden zusammengefasst, Segmentdateien möglichst ausgefiltert. Die Kandidaten stehen nur im Arbeitsspeicher und werden bei einem Seitenwechsel gelöscht. Einige Player verbergen ihre Quellen, insbesondere hinter Service Workern oder individuellen POST-Anfragen. Eine universelle Erkennung ist nicht möglich.

Eine Quelle antippen und **Als MP4 speichern** wählen; direkte Dateien lassen sich alternativ unverändert im Originalformat speichern. Beim ersten Video wird ein Downloadordner gewählt und dauerhaft freigegeben. Danach werden neue Dateien automatisch dort angelegt; gleiche Dateinamen erhalten einen zusätzlichen Zähler. Der Ordner kann unter **Menü → Einstellungen → Video-Downloadordner** geändert werden. Media3 führt bei unterstützten Quellen Ton und Bild in einer MP4-Datei zusammen; falls nötig werden kompatible H.264-/AAC-Spuren erzeugt. Adaptive Quellen verwenden standardmäßig die höchste unterstützte Variante. Für lange oder Live-Quellen gibt es zusätzlich zeitlich begrenzte Ausschnitte. Je nach Live-Zeitleiste funktioniert ein Export nicht oder enthält den verfügbaren Ausschnitt des Streamfensters; keine universelle Live-Aufnahme-Zusage. DRM-/Lizenzschutz, abgelaufene Quellen, fehlende Berechtigungen und nicht unterstützte Codecs werden nicht umgangen. Kopiergeschützte Streams können erkannt werden, der Export schlägt dann jedoch fehl.

Die Exporte laufen als Android-Vordergrunddienst mit Fortschritt. Ab Version 0.4.21 werden je Browserbereich bis zu drei Videos gleichzeitig gespeichert. Weitere Videos warten in ihrer Reihenfolge auf einen freien Platz. Unter **Menü → Einstellungen → Video-Downloads** oder **Videos → Fortschritt** stehen alle laufenden und wartenden Downloads. Jedes laufende Video hat einen eigenen Fortschrittsbalken, Datenmenge, Restzeitschätzung und Abbruchknopf. Neben jedem wartenden Video entfernt **Abbrechen** nur diesen Eintrag. **Alle abbrechen** beendet alle laufenden und wartenden Aufträge. Fehler blockieren die folgenden Downloads nicht. Die Ansicht aktualisiert sich automatisch und zeigt Dateiname, aktuellen Schritt, Prozent (soweit verfügbar), übertragene Datenmenge, durchschnittliche Geschwindigkeit sowie eine Restzeitschätzung für den laufenden Schritt. Herunterladen/MP4-Erstellung und das abschließende Kopieren in den Zielordner werden getrennt angezeigt; erst nach dem Schließen der Zieldatei gilt ein Video als gespeichert. Bei unbekannter Größe oder Live-Streams bleiben Prozent beziehungsweise Restzeit gegebenenfalls offen. Die Restzeit gilt nicht für die gesamte Warteschlange. Bei längerem Stillstand wird die alte Schätzung ausgeblendet. Diese Ansicht funktioniert auch ohne Benachrichtigungsfreigabe. Der Fortschritt steht außerdem in der Systembenachrichtigung; Antippen öffnet dieselbe Live-Ansicht; Android kann deren Anzeige abhängig von den Benachrichtigungseinstellungen begrenzen. HLS/DASH-Exporte benötigen vorübergehend freien internen Speicher für die MP4-Datei, bevor sie zum gewählten Ziel kopiert wird. Abbruch oder Fehler entfernen die temporäre Datei und versuchen die neu erzeugte Zieldatei zu löschen.

Normale und private Exporte verwenden Dienste im jeweiligen Browserprozess. Cookies werden für jede Zieladresse neu aus dem jeweiligen Cookie-Speicher gelesen. Autorisierungs- und Origin-Header bleiben an den ursprünglichen Ursprung gebunden, auch bei Weiterleitungen. Es gibt keine deaktivierte TLS-Prüfung. Private Tabs dürfen für einen laufenden Export geöffnet bleiben; beim Schließen des privaten Bereichs fragt die App, ob alle laufenden und wartenden Downloads abgebrochen werden sollen. Heruntergeladene Dateien bleiben als bewusst gespeicherte Dateien bestehen. Video-Adressen und Zugangsheader werden nicht in einer Download-Datenbank gespeichert.

Tests umfassen Erkennung, Zusammenführen und Zurücksetzen von Quellen, HTTP-Weiterleitungen mit erneuter Cookie-Auswahl und Entfernen fremder Autorisierung sowie MP4-/HLS-/DASH-Exporte auf einem Android-Emulator. Die kleinen Testvideos sind selbst erzeugte Testmuster mit Sinuston; der Gerätetest prüft beide Ton-/Videospuren und deren Dauer in den exportierten MP4-Dateien.

## YouTube-Erkennung (0.3.1)

### YouTube-Downloads (0.3.2)

Beim Öffnen der Videoliste ermittelt zusätzlich NewPipeExtractor v0.26.5 anonyme YouTube-Downloadquellen direkt auf dem Gerät. Der Extractor unterstützt separate YouTube-Client-Abfragen und die Auflösung von Wiedergabeadressen unabhängig vom Web-Player. HLS-Quellen werden zuerst geprüft; andernfalls werden kombinierte Videodateien oder getrennte Bild-/Tonspuren gesucht und auf Erreichbarkeit geprüft. Der bestehende MP4-Export und die Android-Dateiauswahl speichern die Datei. Keine externen Downloadserver. Browsercookies werden nicht an den Extractor weitergegeben. Die Suche läuft im Hintergrund und ist abbrechbar; Ergebnisse bleiben nur im RAM und werden beim Seitenwechsel verworfen. Sicherheitsprüfungen, Anmeldepflicht oder abgelehnte Quellen werden als fehlgeschlagener Download angezeigt, nicht als erfolgreicher Fund.

Der Android-Livetest öffnet den echten Browser sichtbar und versucht einen kurzen YouTube-Ausschnitt mit Bild und Ton zu exportieren. Eine gesperrte Live-Abfrage wird ausdrücklich als nicht verifiziert protokolliert. Die unabhängigen Export-/Speicherisolationstests bleiben verbindlich.

NewPipeExtractor: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5 (GPL-3.0-or-later). Lizenztext liegt in LICENSE und in der APK unter assets/extractor-license.txt. Der Quellcode dieser App wird unter GPL-3.0-or-later bereitgestellt. Die nachfolgenden Hinweise beschreiben den zusätzlich erhaltenen Browser-Playerweg aus Version 0.3.1.

Die App wertet zusätzlich YouTube-Playerdaten (`ytInitialPlayerResponse`, `movie_player.getPlayerResponse()`, Player-Konfiguration und nachgeladene Player-Antworten) aus. Watch-Seiten, mobile Ansichten, Shorts, Live-/Embed-Adressen und Navigation zum nächsten Video innerhalb derselben Seite werden berücksichtigt. Bei unterstützten aktuellen WebViews wird die Beobachtung vor dem Laden der YouTube-Seite und auch in HTTPS-YouTube-Frames eingerichtet. Auf älteren WebViews erfolgt die Auswertung nach dem Laden und beim Öffnen der Videoliste. Ein eng auf HTTPS-YouTube-Ursprünge begrenzter WebMessage-Empfänger nimmt nur Videometadaten entgegen; es gibt keine Schnittstelle zu nativen Datei-, Netzwerk- oder sonstigen Android-Aktionen.

Gefundene Qualitätsstufen werden mit Titel und Auflösung angezeigt. Separate Bild- und Tonspuren werden für den MP4-Export zusammengeführt, statt ein stummes Video anzubieten. Bereits vom Browser aufgelöste Wiedergabe-Adressen werden bevorzugt; Quellen eines zuvor geöffneten Videos dürfen keine aktuellen Quellen ersetzen. Die Erkennung des Videos und die Verfügbarkeit eines Downloads sind getrennt: Wenn YouTube nur SABR-/signaturabhängige Playerdaten oder eine Anmelde-/Verfügbarkeitssperre liefert, zeigt die App **YouTube-Video erkannt** mit einem konkreten Hinweis, aber keinen erfundenen Download. Es gibt keine allgemeine Zusage, dass jedes YouTube-Video heruntergeladen werden kann, und keinen externen Downloadserver.

Tests: JavaScript-Player- und Navigationsbeispiele, Quellen-/Spurzuordnung und Fremddomain-Abweisung; echte Android-WebView-Auswertung einer nachgebildeten YouTube-Playerantwort; MP4-Export separater Video-/Audiotestdateien. Die Testdateien sind selbst erzeugt. YouTube kann Live-Prüfungen von Rechenzentrums-/CI-Adressen blockieren; eine erfolgreiche Parserprüfung ist daher kein Nachweis, dass beliebige YouTube-Downloads auf jedem Gerät funktionieren.


## Eigener Passwortspeicher (0.4.0)

BestBrowser kann Logins nach ausdrücklicher Bestätigung speichern und für dieselbe HTTPS-Website wieder ausfüllen. Benutzername, Passwort und Website-Zuordnung liegen gemeinsam AES-256-GCM-verschlüsselt in einer atomar geschriebenen Datei unter Androids `no_backup`-Verzeichnis. Der nicht exportierbare Schlüssel liegt im Android-Keystore. Es gibt keine Passwortsynchronisation oder Klartext-Passwortdatei. Beschädigte Dateien oder verlorene Schlüssel werden nicht stillschweigend durch einen leeren Speicher ersetzt.

Beim Absenden eines einfachen Login-Formulars fragt die App nach Speichern oder Aktualisieren. Auf aktuellen WebViews erscheint nach Antippen eines Login-Felds eine Auswahl passender gespeicherter Konten. Unter **Menü → Passwörter** gibt es außerdem manuelles Speichern, Ausfüllen, Verwaltung und Löschen sowie einen Schalter für automatische Speichernachfragen. Passwörter werden maskiert dargestellt; Passwortdialoge verhindern Screenshots. Eine eigene Geräte-PIN oder ein Masterpasswort ist nicht enthalten: Die Funktion ist im entsperrten Browser verfügbar.

Die Zuordnung erfolgt zur exakten HTTPS-Origin einschließlich abweichender Ports. Es gibt keine Freigabe für andere Subdomains, andere Websites, HTTP oder eingebettete Frames. Die native Nachrichtenschnittstelle akzeptiert nur Nachrichten des aktuellen Hauptdokuments mit passender Origin und Navigationstoken. Ausfüllen benötigt eine native Kontenauswahl; Origin und Dokument werden unmittelbar davor und nochmals im Seitenskript geprüft. Ein Seitenwechsel zwischen Kontenauswahl und Ausfüllen darf keine Zugangsdaten an die neue Seite übergeben. Es wird kein Login automatisch abgeschickt.

In privaten Tabs wird der Passwortmanager vollständig ausgelassen. Auch Android-Autofill und die alten WebView-Formulardaten-Funktionen bleiben dort aus. Der gewählte eigene Passwortspeicher wird ebenfalls in normalen Tabs anstelle der System-Autofill-Speicherung verwendet. Allgemeines Löschen von Verlauf und Cookies entfernt keine Passwörter; dafür gibt es die eigene Passwortverwaltung. Ohne funktionierenden WebMessage-Support bleiben die expliziten Menüaktionen nutzbar. Komplexe mehrstufige Formulare, mehrere Passwortfelder, geschlossene Shadow-DOMs oder Login-Iframes sind nicht automatisch abgedeckt.

Prüfungen: authentifizierte Verschlüsselung, zufällige Nonces, falsche Schlüssel und manipulierte Dateien; echte Android-Keystore-Persistenz; native Speichernachfrage, Ausfüllen im echten WebView, Wiederherstellung und Login-Löschung; Website-/Port-Trennung, Navigation während der Kontenauswahl, ungültige Nachrichtentoken und Ausschluss privater Tabs. Die Geräteprüfungen laufen auf Android 9 und Android 15, mit ausdrücklich geprüftem WebMessage-Support auf Android 15.

## Kompakte Browserleisten (0.4.1)

Die Adressleiste ist 28 statt 56 dp hoch, die untere Buttonreihe 25 statt 50 dp. Die Symbole bleiben lesbar; dadurch steht mehr Fläche für Webseiten zur Verfügung.

## Video-Warteschlange (0.4.2)

Nach der erstmaligen Wahl des Videoordners wird jedes weitere Video direkt vorgemerkt. Die Benachrichtigung zeigt die Anzahl gleichzeitig aktiver und wartender Downloads sowie die einzelnen Fortschritte. Die Warteschlange bleibt im Arbeitsspeicher, auch beim Wechsel der Webseite; nach einem Prozessabbruch wird sie nicht wiederhergestellt. Normale und private Warteschlangen sind getrennt.

Service-Tests prüfen mit gleichzeitig geöffneten lokalen HTTP-Antworten und getrennten Dokumentzielen drei überlappende Downloads, das Startlimit, die Reihenfolge wartender Aufträge, unabhängige HTTP-Fehler, Einzelabbruch, verspätete Benachrichtigungsaktionen und die Bereinigung auch nach einem verzögert geöffneten Ziel.

## BestBrowser und dauerhafter Videoordner (0.4.3)

Die App heißt BestBrowser. Paketname und Signierung bleiben für Updates sowie vorhandene Tabs, Lesezeichen und verschlüsselte Passwörter erhalten; das GitHub-Repository bleibt MusikMathe. Der Videoordner wird auch nach einem Neustart wiederverwendet und gilt in beiden Browserbereichen. Eine Änderung unter Menü → Einstellungen gilt für neue Downloads; bereits vorgemerkte Dateien behalten ihr Ziel. Normale Datei-Downloads über Androids Downloadmanager bleiben im systemweiten Downloads-Ordner.

Ist der gewählte Videoordner nicht mehr erreichbar oder beschreibbar, wird keine Datei überschrieben und die App bietet eine neue Ordnerwahl an. Tests prüfen die Wiederverwendung nach einem App-Neustart, neue Dateinamen bei Kollisionen, Ordnerwechsel mit bereits angelegten Zielen und fehlgeschlagenen Schreibzugriff.

## Werbeblocker und neues Browser-Icon (0.4.4)

Der eingebaute Werbeblocker ist zunächst eingeschaltet. **Menü → Werbeblocker** schaltet ihn direkt ein oder aus; unter **Menü → Einstellungen → Werbeblocker** gibt es denselben Schalter mit einer kurzen Erklärung. Beim Umschalten wird die aktuelle Seite neu geladen. Die Einstellung gilt für neue Anfragen in allen Tabs des jeweiligen Browserbereichs. Bereits geladene Inhalte in anderen Tabs ändern sich erst nach dem Neuladen dieser Seiten.

Normale Tabs behalten die Auswahl nach einem App-Neustart. Jeder passwortgeschützte private Bereich speichert seine eigene Auswahl und seine Website-Ausnahmen verschlüsselt und verändert die normale Einstellung nicht.

Der Blocker prüft HTTP-/HTTPS-Unteranfragen gegen eine kleine, mitgelieferte Liste bekannter Werbe- und Trackingserver. Er lädt keine Filterlisten herunter, speichert keine besuchten Adressen und blockiert keine ausdrücklich aufgerufene Hauptseite. Treffer erhalten eine leere, nicht zwischenspeicherbare Antwort, bevor sie an die Videoerkennung gelangen. Die Liste wird mit App-Updates gepflegt. Das ist kein vollständiger EasyList-/uBlock-Filter: Werbung aus derselben Quelle wie der Seiteninhalt, viele Videoanzeigen einschließlich YouTube und Anfragen außerhalb der WebView-Abfangfunktion werden damit nicht zuverlässig entfernt. Falls eine Website nicht funktioniert, den Blocker ausschalten und die Seite neu laden. Chrome-Erweiterungen werden weiterhin nicht ausgeführt.

Das neue Launcher-Icon verbindet einen mehrfarbigen Ring, eine blaue Mitte, ein Löwen-/Fuchsmotiv und einen orangefarbenen Schweif. Android 6 und 7 verwenden ein klassisches Drawable; ab Android 8 passt sich das adaptive Icon der Launcher-Form an. Paketname und Entwicklungsschlüssel bleiben erhalten, sodass die APK als Update installierbar ist.

Neue Tests prüfen Domain-Grenzen, Unterdomains, Groß-/Kleinschreibung, erlaubte Hauptnavigation, das Ausschalten, Wiederherstellung und private Sitzungseinstellungen sowie die tatsächliche WebView-Request-Abfangfunktion in mehreren Tabs. Pull Requests führen jetzt ebenfalls den vorhandenen APK-, Test- und Lint-Workflow aus.

## Erweiterter Werbeblocker und VPN-Zugänge (0.4.5)

Der Werbeblocker enthält jetzt HaGeZi Multi LIGHT mit 65.249 mitgelieferten Servereinträgen sowie rund 13.600 allgemeinen und 8.800 seitenspezifischen EasyList-CSS-Regeln einschließlich Ausnahmen. Die normalen CSS-Regeln greifen auch bei nachträglich eingefügten Werbeelementen. Bei aktuellen WebViews werden sie am Dokumentanfang eingerichtet; ältere Provider verwenden Seiten-Callbacks. Nicht unterstützte prozedurale Filter, Scriptlets und vollständige EasyList-Netzwerksyntax sind nicht implementiert. Regeln innerhalb fremder Frames, restriktive Content-Security-Policy in älteren WebViews, Service Worker und eingebettete Videoanzeigen können die Filterung begrenzen. Eine zuverlässige YouTube-Videoanzeigen-Sperre wird weiterhin nicht zugesagt.

**Menü → Werbeblocker** öffnet den Ein-/Ausschalter, eine Ausnahme für die aktuelle Website und einen Zähler für abgefangene Anfragen. Eine Ausnahme gilt für den angezeigten Host und seine Unterdomains; im normalen Browser bleibt sie gespeichert, in passwortgeschützten privaten Bereichen wird sie im jeweiligen Bereich verschlüsselt aufbewahrt. **Serverliste aktualisieren** lädt ausschließlich die öffentliche HaGeZi-Domainliste über HTTPS, ohne Browsercookies oder besuchte Adressen zu senden. Fehlerhafte, zu große oder unvollständige Antworten ersetzen die bisherige Liste nicht. Die gültige Liste wird atomar unter no_backup gespeichert. EasyList-CSS-Regeln werden mit App-Updates aktualisiert. Quelle und Lizenzstände liegen unter filters/; alle Filter arbeiten auch ohne vorherigen Download.

Abgefangene Netzwerkressourcen verwenden einen fehlschlagenden Eingabestrom statt einer erfolgreichen leeren HTTP-204-Antwort. Das verhindert, dass Fetch-/HEAD-Anfragen so aussehen, als hätten sie den Werbeserver erreicht. Manche WebViews oder Testseiten können blockierte Anfragen dennoch anders bewerten. Die CSS-Prüfungen der vom Nutzer genannten Testseite adblock.turtlecute.org sind mit zwei transparent dokumentierten Website-Regeln berücksichtigt; weder Messwerte noch Test-Anzeigen werden manipuliert. Der Testwert allein ist kein Nachweis der gesamten Filterabdeckung.

Unter **Menü → VPN** gibt es beide Wege: **Proton VPN Free** als kostenlosen Anbieter und **vorhandene VPN-Zugänge** über WireGuard, OpenVPN Connect oder Androids VPN-Einstellungen. Die jeweilige echte VPN-App baut den Tunnel auf; BestBrowser nutzt die Android-VPN-Verbindung. Fehlt die App, wird ihre konkrete Play-Store-Seite geöffnet, alternativ die offizielle Anbieterseite in einem neuen Tab. BestBrowser enthält selbst keinen VPN-Tunnel, stellt keinen Server bereit und speichert keine VPN-Schlüssel oder Anmeldedaten. Es werden weder Konten angelegt noch kostenpflichtige Optionen gebucht. Bei Proton ist ein eigenes Konto nötig. Eine externe App kann das gesamte Gerät schützen; Split-Tunneling-Einstellungen können BestBrowser ausschließen.

Der VPN-Dialog zeigt ausschließlich, ob Android für den Aufrufer eine VPN-Verbindung meldet. Unbekannte oder fehlende Zustände werden entsprechend angezeigt. Ein erkannter VPN-Transport beweist weder einen erreichbaren VPN-Server noch eine andere öffentliche IP-Adresse; dafür gibt es den bewusst auswählbaren Link zur IP-Prüfung. Ein kostenloses VPN ersetzt keine Werbefilter.

Tests umfassen native WebView-Fetch-/HEAD-Fehler, wirklich ausgeführte bzw. gesperrte Werbeskripte, statische und dynamische CSS-Werbeelemente, Positivkontrollen für normale Inhalte, Website-Ausnahmen und Ausschalten, Filterdaten-Validierung sowie die ehrliche VPN-Statusanzeige. Die kontrollierten WebView-Prüfungen benötigen keine externen Websites und laufen auf Android 9 und Android 15. Ein bestimmter Live-Test-Prozentwert oder eine echte VPN-Verbindung ohne Nutzerzugang wird dadurch nicht bestätigt.


## Doppeltipp-Vollbild (0.4.6)

Ein Doppeltipp beziehungsweise Doppelklick auf eine freie Fläche der Webseite oder der Browserleiste schaltet den Vollbildmodus ein. Die untere Browserleiste und die Android-Systemleisten werden ausgeblendet. Oben bleiben seit 0.4.23 eine kompakte Adresse und Navigation neben der Kamera sichtbar. Erneutes Doppeltippen auf freie Fläche oder die Zurück-Taste beendet den Browser-Vollbildmodus, ohne dabei im Verlauf zurückzugehen. Die Android-Leisten lassen sich im Vollbild vorübergehend vom Bildschirmrand einblenden. Alternativ ist der Modus über Menü → Vollbild erreichbar.

Links, Schaltflächen, Formulare, Medien, Text und andere erkennbare Bedienelemente lösen den Modus nicht aus. Die Prüfung erfolgt an beiden Berührungspositionen im aktuellen Dokument; es wird keine JavaScript-Schnittstelle mit nativen Vollbildrechten auf Webseiten veröffentlicht. Scrollen, Pinch-Zoom und lange Berührungen bleiben erhalten. Der Modus funktioniert in normalen und privaten Tabs. Video-Vollbild bleibt getrennt: Nach dem Schließen des Videos wird der vorherige Browser-Vollbildzustand wiederhergestellt.


## Vorschläge aus dem Verlauf (0.4.8)

Beim Tippen in die Adressleiste erscheinen bis zu acht passende besuchte Seiten mit Titel und genauer Webadresse. Der Abgleich berücksichtigt Adresse und Seitentitel ohne Unterscheidung von Groß- und Kleinschreibung sowie mehrere Suchwörter. Adressanfänge stehen vor anderen Treffern; innerhalb einer Treffergruppe stehen zuletzt besuchte Seiten oben. Antippen öffnet die gespeicherte Adresse. Bei angeschlossener Tastatur wählen die Pfeiltasten einen Vorschlag, Enter öffnet ihn. Ohne ausgewählten Vorschlag öffnet Enter die eingegebene Adresse beziehungsweise die gewählte Suchmaschine.

Die Vorschläge werden ausschließlich lokal ermittelt. Tippen löst keine Webseitenaufrufe oder Abfragen bei einem Vorschlagsdienst aus. Leere Eingaben zeigen keine Vorschläge. Gelöschte Verlaufseinträge werden nicht weiter angeboten. Private Bereiche lesen keinen normalen Verlauf. Ab Version 0.4.19 stammen ihre Verlaufsvorschläge ausschließlich aus dem eigenen, mit dem jeweiligen Passwort geöffneten Verlauf.


## Dezentes Icon auf der Startseite (0.4.9)

Die lokale Startseite zeigt das vorhandene Browser-Icon als großes, blasses Hintergrundmotiv. Die Deckkraft beträgt 7 Prozent im hellen und 9 Prozent im dunklen Modus. Das Motiv bleibt hinter dem Inhalt und fängt keine Berührungen ab. Es verwendet die bereits enthaltene Android-Bildressource, ohne zusätzliche Netzwerkabfrage.


## Vollbild bis zum Bildschirmrand (0.4.10)

Browser- und Video-Vollbild nutzen auch die Bildschirmfläche um den Kameraausschnitt. Dafür wird Android ab Version 9 das Zeichnen in diesem Bereich erlaubt; ab Android 11 gilt dies für alle Bildschirmränder. Im Vollbild fügt die App keinen Abstand für den Kameraausschnitt hinzu. Der Abstand für eine eingeblendete Bildschirmtastatur bleibt erhalten. Nach dem Verlassen des Vollbilds werden Browserleisten und ihre Schutzabstände wiederhergestellt.


## Übersichtliches Browsermenü (0.4.11)

Das Menü orientiert sich an Chrome: Eine obere Icon-Leiste bietet Vorwärts, Lesezeichen setzen/entfernen, Teilen und Neuladen/Stoppen. Die beschrifteten Einträge darunter erhalten passende Vektor-Icons und werden durch Trennlinien in Tabs, Bibliothek, Seitenwerkzeuge und Schutzfunktionen gegliedert. Desktopwebsite zeigt einen Haken; der Werbeblocker zeigt seinen Ein-/Aus-Status. Einstellungen bleiben am unteren Rand sichtbar, während die Liste auf kleinen Bildschirmen scrollt. Suchmaschine, Browserdaten löschen und die Versionsinformation sind unter Einstellungen erreichbar. Icons besitzen gesprochene Beschriftungen, die Menüzeilen passen sich größerer Schrift an.


## Nachtmodus (0.4.12)

Unter Menü → Nachtmodus lässt sich die dunkle Darstellung ein- und ausschalten; alternativ steht der Schalter unter Einstellungen. Die Auswahl bleibt nach einem Neustart gespeichert. Neue private Bereiche übernehmen zunächst die normale Darstellung; anschließend speichert jeder Bereich seine eigene Auswahl verschlüsselt, ohne die normale Darstellung zu ändern.

Browserleisten, Adressvorschläge, das Icon-Menü, Dialoge und die lokalen Start-/Wikipedia-Seiten erhalten dunkle Flächen und helle Schrift. Offene WebViews bleiben beim Umschalten erhalten: Formulare, Scrollposition und laufende Seiten werden nicht neu geladen. Aktuelle WebViews verwenden den Nachtmodus der Website beziehungsweise die native automatische Abdunklung; Bilder und Videos werden nicht pauschal invertiert. Alte WebViews ohne diese Funktionen erhalten eine einfache dunkle Grunddarstellung für Hintergrund, Text und Formulare; komplexe Webseiten können dort weiterhin helle Teilflächen enthalten.


## Einzelne wartende Videos abbrechen (0.4.13)

Unter Menü → Einstellungen → Video-Downloads steht neben jedem wartenden Video eine eigene Schaltfläche **Abbrechen**. Sie entfernt nur den ausgewählten Auftrag und räumt dessen vorbereitete Zieldatei auf. Der laufende Download und die Reihenfolge der übrigen Videos bleiben erhalten. Auch gleichnamige Videos werden eindeutig unterschieden. Ist der ausgewählte Auftrag inzwischen gestartet oder schon entfernt, bewirkt ein veralteter Tastendruck keinen Abbruch eines laufenden oder anderen Videos; die Ansicht aktualisiert sich. Das gilt getrennt für den normalen und privaten Browserbereich.

Service-Tests prüfen identische Titel, die Entfernung aus der Mitte der Warteschlange, die unveränderte Reihenfolge und veraltete Schaltflächen nach dem Start eines Auftrags. Der Gerätetest bedient die einzelne Schaltfläche während einer angehaltenen lokalen HTTP-Übertragung, prüft die gelöschte Zieldatei und bestätigt, dass der entfernte Download nie angefragt wird.


## Geschützte Medien (0.4.14)

HTTPS-Websites können für die Wiedergabe geschützter Videos und Musik eine Freigabe anfragen. Der Dialog **Geschützte Inhalte** zeigt die anfragende Website und erklärt die Gerätekennung für die Lizenzprüfung. Erst **Erlauben** reicht die angefragte geschützte Medienkennung an den aktuellen WebView weiter; **Ablehnen**, ein Seiten- oder Tabwechsel und abgebrochene Anfragen geben sie nicht frei. Die Freigabe wird vom Browser nicht als allgemeine oder dauerhafte Website-Erlaubnis gespeichert. Kamera und Mikrofon erhalten weiterhin getrennt die erforderlichen Android-Berechtigungen; unbekannte Ressourcen werden nicht freigegeben. Anfragen anderer Ursprünge und unsicherer HTTP-Seiten werden abgelehnt.

Die Freigabe beseitigt die bisherige pauschale Ablehnung von `PROTECTED_MEDIA_ID`. Sie installiert kein Widevine-Modul und garantiert keine Netflix-Kompatibilität. Netflix-Fehler M7701-1003 kann auch durch fehlende Unterstützung des Browsers oder des Geräts entstehen. Die auf der Netflix-Fehlerseite genannte Chrome-Einstellungsadresse gibt es in BestBrowser nicht. Nach dem Update Netflix neu laden und eine gegebenenfalls erscheinende Abfrage mit **Erlauben** beantworten. Bei weiter bestehendem Fehler die offizielle Netflix-App verwenden. [Netflix-Hilfe zum Fehler](https://help.netflix.com/de/node/27451).

Unit-Tests prüfen Zustimmung, Ablehnung, Herkunft, Navigation, private Tabs, gemischte Ressourcen und verspätete Abbruchmeldungen. Der Gerätetest löst eine gezielte Anfrage am echten WebChromeClient aus und bedient die native Abfrage auf Android 9 und 15. Er simuliert die geschützte Medienanfrage; eine echte Netflix-Wiedergabe oder erfolgreiche Widevine-Lizenzierung wird damit nicht nachgewiesen.

## Videos auf Chromecast und Google TV übertragen (0.4.15)

**Menü → Auf Fernseher übertragen** öffnet Geräteauswahl und Wiedergabesteuerung. Handy und Cast-fähiger Fernseher beziehungsweise Chromecast müssen im selben WLAN sein; aktuelle Google Play-Dienste werden benötigt. Video auf der Webseite starten, **Video auswählen** antippen und eine erkannte Quelle wählen. Alternativ bietet die vorhandene Videoliste die Aktion **Auf Fernseher übertragen**. Nach der Gerätewahl lädt der Fernseher die Quelle selbst. Pause, Fortsetzen, Stopp und Verbindung trennen stehen im Cast-Dialog bereit; Schließen des Dialogs beendet die Wiedergabe nicht.

Die erste Version unterstützt direkt erreichbare MP4-, WebM-, HLS- und DASH-Quellen, soweit Format, Codecs und bei adaptiven Streams die CORS-Freigaben des Servers zum Empfänger passen. Es ist keine Bildschirmspiegelung. `blob:`-Adressen, lokale Handydateien, getrennte Bild-/Tonquellen und bekannte geschützte Quellen werden nicht angeboten. Netflix und YouTube benötigen den Übertragungsweg des jeweiligen Anbieters beziehungsweise dessen TV-App; diese Funktion behebt die Netflix-WebView-Fehler nicht. Browsercookies, Anmeldeheader und Referer werden nicht an den Empfänger weitergereicht. Manche Quellen scheitern daher an Anmeldung oder Hotlink-Schutz. Die ausgewählte Medienadresse und der Seitentitel werden an den Cast-Empfänger übermittelt; Medienadressen können kurzlebige Zugriffstoken enthalten.

Cast wird erst nach einer bewussten Aktion in einem normalen Tab initialisiert. In privaten Tabs bleibt es ausgeschaltet. Gespeicherte Cast-Sitzungen werden nicht automatisch wiederhergestellt. Wird während der Geräteauswahl die Seite oder der Tab gewechselt, startet eine veraltete Auswahl nicht. Ohne Google Play-Dienste bleibt der Browser nutzbar und erklärt die fehlende Voraussetzung.

Verwendet wird das Google Cast Android Sender SDK 22.3.1 mit dem Default Media Receiver. Tests prüfen Quellenfilter, tatsächliche SDK-Medienmetadaten, private Tabs, Seitenwechsel, Steuerbefehle und verspätete Ergebnisse. Gerätetests prüfen Menü, Videoaktion und fehlende Play-Dienste auf Android 9 und 15. Die CI besitzt keinen Cast-Empfänger; eine echte Geräteverbindung oder TV-Wiedergabe ist damit nicht nachgewiesen. [Google-Cast-Integration](https://developers.google.com/cast/docs/android_sender/integrate), [Standardempfänger](https://developers.google.com/cast/docs/web_receiver).


## Startseiten-Kacheln mit Icons (0.4.16)

Die Startseite enthält zusätzlich **Joyn**, **RTL** und **ZDF**. Antippen öffnet direkt `https://www.joyn.de/`, `https://www.rtl.de/` beziehungsweise `https://www.zdf.de/`. Alle Schnellzugriffe und die Einträge unter Deutschland entdecken haben lokal eingebettete Vektor-Icons. Die Icons laden ohne Netzwerkanfrage und bleiben im Nachtmodus lesbar.


## Kleine Kacheln und persönliche Top 3 (0.4.17)

Die Startseite zeigt kompakte Kacheln mit Icon und Beschriftung in drei Spalten. Unter **Meistbesucht** stehen bis zu drei Websites aus dem gespeicherten normalen Verlauf. Besuche mehrerer Unterseiten derselben Website werden zusammengezählt; `www.` und die übliche HTTP-/HTTPS-Adresse zählen zusammen. Bei gleicher Häufigkeit entscheidet der jüngste Besuch. Die Kachel öffnet die am häufigsten besuchte gespeicherte Unterseite, bei Gleichstand die jüngste. Bekannte Websites verwenden ihr vorhandenes Icon, andere ein lokales Buchstaben-Icon. Es gibt keine Favicon-Abfragen.

Besuchszahlen werden ab diesem Update pro abgeschlossener Navigation mitgespeichert; wiederholte Abschlussmeldungen derselben Navigation zählen nicht zusätzlich. Alte Verlaufseinträge beginnen mit einem bekannten Besuch, da frühere Wiederholungen nicht gespeichert wurden. Die Auswertung gilt für die maximal 500 aufbewahrten Verlaufseinträge. Das Löschen des Verlaufs entfernt auch die Häufigkeiten und die persönlichen Kacheln auf bereits geöffneten Startseiten. Passwortgeschützte private Bereiche zeigen ab Version 0.4.19 ausschließlich ihre eigenen Top-Seiten und zählen ihre Besuche im eigenen Verlauf.


## Vorladen im WLAN und Suchkacheln (0.4.18)

**Menü → Einstellungen → Links im WLAN vorladen** bietet einen Ein-/Ausschalter und den aktuellen Status. Die Auswahl bleibt nach einem Neustart gespeichert; im normalen Browser ist sie zunächst eingeschaltet, in privaten Tabs immer ausgeschaltet. Nur eine von Android eindeutig als WLAN ohne Datenbegrenzung gemeldete Verbindung erlaubt das Vorladen. Mobilfunk, unbekannte Verbindungen und getaktetes WLAN bleiben ausgeschlossen; ein VPN muss seine WLAN-Verbindung erkennen lassen.

Nach dem Laden der sichtbaren Seite werden geeignete HTTPS-Links gesammelt und über AndroidX WebKit 1.14.0 zum nativen Vorladen an WebView übergeben. Dabei wird nur das HTML vorbereitet; verlinkte Seiten und deren JavaScript werden nicht ausgeführt. Die normale Browsernavigation und der Verlauf bleiben unverändert. Es werden keine Links von vorgeladenen Seiten weiterverfolgt. Die lokale Startseite kann ebenfalls Links liefern. Neue Links auf dynamischen Seiten werden etwa alle zehn Sekunden berücksichtigt. Downloads, Links mit Download-Attribut, erkennbare Anmelde-/Abmelde-/Lösch-/Kaufaktionen, API-Adressen, gesponserte Links und vom Werbeblocker gesperrte Ziele werden ausgelassen. Eine URL-Prüfung kann die Wirkung beliebiger Server-Endpunkte nicht vollständig erkennen.

Zur Begrenzung von Datenverbrauch und Akkulast laufen höchstens zwei Anfragen gleichzeitig. Pro angezeigter Seite werden maximal 512 geeignete Ziele aus bis zu 2048 unterschiedlichen DOM-Links berücksichtigt. Der native Zwischenspeicher ist, soweit von WebView unterstützt, auf 64 HTML-Antworten mit 120 Sekunden Lebensdauer eingestellt. WebView entscheidet selbst über die Eignung und Wiederverwendung, etwa bei Cookies und Service Workern. Eine aktuelle WebView mit Unterstützung für native URL-Prefetches ist erforderlich; andernfalls zeigt der Dialog einen Aktualisierungshinweis. Die Funktion verwendet keine eigenen HTTP-Caches oder zusätzlichen Cookie-Kopien. [Android-Dokumentation](https://developer.android.com/develop/ui/views/layout/webapps/speculative-loading).

Bei WLAN-Verlust, Seiten-/Tabwechsel, Wechsel in den Hintergrund oder Ausschalten werden keine neuen Vorladeanfragen gestartet. Laufende Vorladeanfragen werden nach Möglichkeit abgebrochen; bereits versendete Daten können dadurch nicht zurückgeholt werden. Ein direkt ausgewähltes Linkziel darf seine laufende native Anfrage für die echte Navigation weiterverwenden. Ausschalten, Tabwechsel und Browserdaten löschen leeren außerdem die vom Browser nachverfolgten Vorladeeinträge.

Direkt nach Google stehen vier weitere kleine Kacheln mit lokal eingebetteten Vektor-Icons: **Yandex** (`https://yandex.com/`), **SearXNG** (`https://search.inetol.net/`), **Brave Search** (`https://search.brave.com/`) und **Mojeek** (`https://www.mojeek.com/`). SearXNG verwendet eine öffentliche, unabhängig betriebene Instanz; Verfügbarkeit und angebotene Suchquellen hängen von deren Betreiber ab.

Unit-Tests prüfen die Linkauswahl, Begrenzung und Deduplizierung der Warteschlange, verspätete Rückmeldungen, Navigationsübernahme, WLAN-/Mobilfunk-/VPN-Zustände sowie die gespeicherte Einstellung und private Tabs. Gerätetests bedienen den echten Schalter und prüfen Links aus einem tatsächlich gerenderten Dokument mit kontrolliertem Transport. Ein zusätzlicher Test meldet die native Vorlade-Unterstützung und das Ergebnis einer öffentlichen HTTPS-Anfrage, falls der installierte WebView die API anbietet. Die kontrollierten Tests belegen keine bestimmte Beschleunigung auf fremden Websites.


## Passwortgeschützte private Bereiche (0.4.19)

Ein Passwort mit 4 bis 256 Zeichen (ab 0.4.20) öffnet stets denselben privaten Bereich. Ein unbekanntes Passwort legt erst nach wiederholter Eingabe einen neuen Bereich an; so überschreibt ein Tippfehler keine vorhandenen Daten. Die Passwortabfrage ist eine native, gegen Screenshots und Autofill geschützte Ansicht. Das Passwort wird nicht gespeichert, an Webseiten übergeben oder in Intents beziehungsweise Androids Zustands-Bundles geschrieben. Ohne das Passwort gibt es keine Wiederherstellung. Alle Daten bleiben lokal; Deinstallation oder Löschen der App-Daten entfernt die Bereiche.

Jeder Bereich behält bis zu 500 Verlaufseinträge, eigene häufig besuchte Seiten und Adressvorschläge, bis zu 20 offene Tabs, Lesezeichen, die Suchmaschine, Werbeblocker-Auswahl samt Ausnahmen, Nachtmodus und den unter **Einstellungen → Video-Downloadordner** gewählten Ordner. Anfangs ist für einen neuen Bereich kein Videoordner gewählt. Website-Cookies, localStorage, IndexedDB und weitere beständige WebView-Daten werden ebenfalls wiederhergestellt. Website-seitige Ablaufzeiten und Abmeldungen gelten weiterhin. Die Tabs werden mit ihren Adressen wieder geöffnet; flüchtige JavaScript-Zustände, laufende Medien, ungesendete Formulare und die Android-Zurück-Liste werden nach einem Prozessende nicht vollständig wiederhergestellt.

Im Hintergrund wird der Inhalt ausgeblendet. Bei einer Rückkehr innerhalb von fünf Minuten bleibt der bereits authentifizierte Bereich geöffnet. Erst nach mehr als fünf Minuten im Hintergrund ist sein Passwort erneut erforderlich. Die Frist verwendet Androids monotone, auch während des Ruhezustands weiterlaufende Zeitmessung. Ein vollständiges Ende des privaten Prozesses, ein App-Neustart oder bewusstes Sperren verlangt weiterhin das Passwort. Dasselbe Bereichspasswort entsperrt ihn; Zurück umgeht die Abfrage nicht. Bereits laufende Video-Downloads dürfen dabei weiterlaufen. **Zu normalen Tabs** oder **Privaten Bereich sperren** beendet den Bereich; laufende beziehungsweise wartende private Downloads müssen dafür nach der vorhandenen Nachfrage abgebrochen werden. Anschließend lässt sich mit einem anderen Passwort ein anderer Bereich öffnen. **Alle privaten Tabs schließen** leert zusätzlich seine gespeicherte Tab-Liste. Verlauf, Lesezeichen, Website-Daten und Einstellungen lassen sich innerhalb des geöffneten Bereichs mit den üblichen Bedienelementen ändern oder löschen.

Die Passwortableitung verwendet PBKDF2-HMAC-SHA256 mit installationsspezifischem 32-Byte-Salz und 210.000 Iterationen. Getrennte abgeleitete Schlüssel schützen die Metadaten mit AES-256-GCM, den Website-Speicher mit Google Tink AES-256-GCM-HKDF-Streaming und die nicht sprechenden Bereichskennungen. Neue Nonces und an Bereich sowie Datentyp gebundene Zusatzdaten verhindern Wiederverwendung oder Vertauschen von Dateien. Metadaten und Archive werden atomar geschrieben. Ein gemeinsamer Dateilock koordiniert beide App-Prozesse. Website-Datenbanken werden erst nach Ende des privaten WebView-Prozesses archiviert. Die Wiederherstellung erfolgt in einen temporären Ordner und wird erst nach vollständiger Authentifizierung übernommen. Manipulierte, unvollständige oder unlesbare Bestände werden nicht durch leere Profile ersetzt. Erneut ladbare Ressourcen-Caches werden ausgelassen; beständige Website-Daten sind auf 1 GiB und 30.000 Dateien pro Bereich begrenzt. Bei einem Sicherungsfehler bleiben Arbeitsdaten und Wiederherstellungsschlüssel für einen erneuten Versuch erhalten, statt vorhandene Daten zu löschen.

Während ein Bereich verwendet wird, liegen seine geöffneten Website-Datenbanken im appinternen Arbeitsverzeichnis und Schlüssel im Prozessspeicher. Der Android-Sandboxschutz bleibt dabei erforderlich. Nach Schließen und erfolgreicher Sicherung sind seine beständigen Browserdaten nur verschlüsselt vorhanden. Bei einem abrupten App-/Gerätestopp kann dieser Abschluss erst beim nächsten Start erfolgen. Bewusst in einen Android-Ordner exportierte Videos sind gewöhnliche Dateien außerhalb dieses Passwortschutzes; der je Bereich gewählte Pfad selbst wird verschlüsselt gespeichert. Cast und automatisches WLAN-Vorladen bleiben in privaten Bereichen ausgeschaltet.

Prüfungen: Zuordnung zweier Passwörter, unabhängige Verläufe/Tabs/Lesezeichen/Einstellungen/Videoordner, verschlüsselte Dateien und zufällige Nonces, falsche Zuordnungen, manipulierte oder abgeschnittene Streams und Archivpfade. Der Gerätetest verwendet die echte Passwortabfrage, Android Keystore und WebView-Cookie-/Website-Datenbanken auf Android 9 und 15, einschließlich Hintergrundsperre und gezieltem Ende nur des privaten Prozesses.


## Video-Vorschaubilder (0.4.20)

Die Download-Auswahl zeigt neben jedem Videonamen ein kleines Vorschaubild. Es stammt aus dem Poster des jeweiligen HTML-Videos oder, soweit bereits geladen und vom Browser zugelassen, aus einem verkleinerten Videobild. YouTube-Quellen erhalten das Thumbnail ihrer Video-ID. Die Auswahl der Speicherart zeigt den gewählten Film mit demselben Bild und Namen nochmals an. Ohne zugängliches Vorschaubild erscheint ein Videosymbol.

Vorschaubilder werden erst beim Öffnen der Auswahl geladen, nur im Arbeitsspeicher gehalten und beim Schließen freigegeben. Bildanfragen sind begrenzt (zwei parallel, maximal 2 MiB pro Bild, Zeitlimit), übertragen keine Stream-Authorization und folgen keiner HTTPS-zu-HTTP-Weiterleitung. Cookies werden jeweils für das konkrete Bildziel aus dem geöffneten Browserbereich gelesen. Für ein Vorschaubild wird kein zusätzlicher Videostream heruntergeladen. Auch private Dialoge und ihre Vorschaubilder werden beim Wechsel in den Hintergrund geschlossen.


## Parallele Video-Downloads (0.4.21)

Bis zu drei Downloads laufen gleichzeitig pro normalem beziehungsweise privatem Browserbereich. Jeder Auftrag besitzt eigene Quellen, temporäre Datei, Fortschrittsmessung und gegebenenfalls einen eigenen Media3-Exporter. Ein begrenzter Worker-Pool verarbeitet Originaldateien und das Speichern der fertigen MP4-Dateien. Mehrere Exporte laufen unabhängig nebeneinander; ein zusätzlicher Android-Prozess je Datei ist dafür nicht erforderlich. Normale und private Dienste behalten ihre getrennten Prozesse und benutzen verschiedene Benachrichtigungs-IDs.

Weitere Videos bleiben in der Warteschlange. Ein Fehler oder der Abschluss eines Downloads gibt nur seinen eigenen Platz frei. Beim Abbruch bleibt der Platz bis zum Schließen der zugehörigen Streams belegt; erst danach beginnt der nächste Auftrag. Der Abbruch einer wartenden Datei kann keine inzwischen gestartete Datei treffen. Einzeln adressierte Benachrichtigungsaktionen können keinen späteren Download abbrechen. Das Schließen eines privaten Bereichs nach „Abbrechen und schließen“ wartet auf die Bereinigung seiner laufenden Downloads. Es wird keine gesamte Restzeit über mehrere Aufträge geschätzt. Die Aufträge bleiben im Arbeitsspeicher und werden nach einem Prozessende weiterhin nicht wiederaufgenommen.

Prüfungen: echte gleichzeitige HTTP-Übertragungen, getrennte Fortschritte und Ausgabedateien, Einzelabbruch bei gleichen Titeln, Warteschlangen-Start nach Freigabe eines Platzes, HTTP-Fehler, veraltete Aktionen, verzögerte Dokumentanbieter sowie zwei parallele MP4-Exporte zusammen mit einem Original-Download. Gerätetests prüfen die Live-Ansicht und spielbare Ausgaben mit Audio- und Videospuren auf Android 9 und 15.


## Videoinformationen vor dem Download (0.4.22)

Die Videoauswahl zeigt neben dem Vorschaubild direkt Auflösung und Dauer. Zusätzlich erscheinen verfügbare Qualitätsstufen, Dateigröße, Format/MIME-Typ, Bildrate, Codecs, Bitraten, Tonspuren, Sprache und Untertitel. „Alle Informationen“ öffnet eine scrollbare und kopierbare Detailansicht mit allen erfassten Angaben, einschließlich Titel, Autor/Kanal, Veröffentlichungsdatum, Beschreibung, Kategorie, Lizenz, Schlagwörtern und Aufrufzahlen, soweit die Quelle sie liefert. Auch die Auswahl der Speicherart besitzt eine Schaltfläche „Informationen“. Von dort bleibt das Speichern erreichbar.

Die Daten stammen aus dem tatsächlich geladenen HTML-Videoplayer, aus den zur jeweiligen Qualität gehörenden YouTube-Player-/Extractor-Daten und bei Streams aus dem Manifest. Die Abmessungen des dargestellten Players werden nicht mit der Videoauflösung verwechselt. Alternative `<source>`-Qualitäten erben nicht die Auflösung oder Dauer einer anderen gewählten Quelle. Fehlende Angaben erscheinen als „nicht angegeben“. Unbekannte Werte werden nicht geschätzt. Bei adaptiven Streams werden mehrere Qualitätsstufen angegeben; bei Live-Playlists wird das aktuelle Zeitfenster von der gesamten Dauer unterschieden. Die Größe eines HLS-/DASH-Manifests ist keine Videodateigröße. Bei getrennten Video- und Audioquellen werden beide Größen getrennt angezeigt; die spätere MP4-Größe wird nicht behauptet.

Beim Öffnen der Auswahl werden zusätzliche Angaben begrenzt nachgeladen: direkte Dateien per HEAD, HLS/DASH durch ein Manifest von höchstens 1 MiB, ohne Segmente oder ganze Videos nachzuladen. Bis zu zwölf Quellen je geöffnetem Dialog werden automatisch geprüft, zwei Bild-/Metadatenaufgaben laufen parallel; die Detailansicht kann gezielt eine weitere Quelle prüfen. Ein Abruf besitzt ein Gesamtzeitlimit von acht Sekunden. Angaben bleiben im Arbeitsspeicher, erfolgreiche Abfragen werden kurz wiederverwendet und alle Dialoganfragen werden beim Schließen abgebrochen. HTTPS-zu-HTTP-Weiterleitungen sind gesperrt; Cookies werden für jedes Ziel neu ermittelt und origin-gebundene Anmeldedaten bleiben an ihre Quelle gebunden. Header, Cookies und geheime URL-Parameter werden in den Videoinformationen nicht angezeigt. Private Ansichten behalten ihren Screenshot- und Hintergrundschutz.

Prüfungen: DOM-/YouTube-Metadaten, Auswahl verschiedener Qualitäten, Zuordnung nach Katalogaktualisierungen, unbekannte und Live-Werte, HLS-/DASH-Varianten und Sprachen, endliche Playlist-Dauer versus Live-Zeitfenster, getrennte Dateigrößen, begrenzte Manifestdaten und HEAD-Weiterleitungen mit getrennten Zugangsdaten. Der Gerätetest zeigt ein echtes 160 × 96-Video mit zwei Sekunden Dauer und bekannter Dateigröße in der Liste und Detailansicht und erreicht anschließend die Speicheraktionen auf Android 9 und 15.


## Kompakte Leiste im Vollbild (0.4.23)

Im Browser-Vollbild sitzt die verkleinerte Adressleiste links neben dem mittigen Kameraausschnitt. Rechts stehen Startseite, Zurück, Vorwärts, Videos und Menü. Es sind dieselben Bedienelemente wie im normalen Modus: Verlaufsvorschläge, Videofundzahl und Navigationszustand bleiben erhalten. Die untere Leiste entfällt. Die Adressschrift ist 12 sp groß, die Bedienzeile 28 dp hoch; bei einer höheren Kameraaussparung nutzt sie deren Höhe.

Die Aufteilung verwendet die tatsächlichen Android-Kameragrenzen. Im Querformat bleiben die Tasten außerhalb einer seitlichen Aussparung. Bei einer sehr breiten oder seitlichen oberen Aussparung mit zu wenig Platz liegen die Bedienelemente direkt darunter. Ohne Aussparung teilen sie sich eine schmale obere Zeile. HTML-Video-Vollbild behält die gesamte Bildschirmfläche ohne Browserleiste.

Die Zurück-Schaltfläche in der kompakten Leiste navigiert im Verlauf und bleibt im Vollbild. Die Android-Zurück-Taste, ein erneuter Doppeltipp auf freie Fläche oder Menü → Vollbild beenden stellen die normalen Leisten wieder her. Tests prüfen die fünf Schaltflächen, die Adressnavigation, den Erhalt der WebView und echte emulierte Kameraaussparungen im Hoch- und Querformat auf Android 9 und 15.


## Zweites Fenster und schwebende Videos (0.4.24)

Menü → **Zweites Fenster öffnen** öffnet ein eigenes Browserfenster. Beide Fenster behalten ihre eigenen Tabs und den jeweils ausgewählten Tab, auch beim erneuten Öffnen. Verlauf, Lesezeichen, normale Cookies, Website-Daten und Einstellungen werden gemeinsam genutzt. In der Android-App-Übersicht lassen sich beide Fenster auswählen.

Für ein schwebendes Video:

1. Ein Video abspielen und direkt im Videoplayer auf Vollbild schalten.
2. Oben rechts auf **Video schweben lassen** (kleines Fenstersymbol) tippen.
3. Im automatisch geöffneten anderen Browserfenster weitersurfen.

Das bereits laufende Video bleibt in derselben WebView; es wird nicht neu geladen oder über einen Download-Extractor erneut gestartet. Androids Bild-in-Bild-Fenster lässt sich verschieben und über die Systemsteuerung vergrößern oder schließen. Die eigenen Aktionen pausieren das Video, setzen es fort oder bringen das andere Browserfenster nach vorn. Beim Schließen beziehungsweise Entfernen des Videofensters endet die Wiedergabe. Android 8 oder neuer, Geräteunterstützung und die aktivierte Bild-in-Bild-Erlaubnis sind erforderlich. Es wird keine Berechtigung zum Überlagern anderer Apps benötigt. Die Wiedergabemöglichkeiten von YouTube und anderen Anbietern richten sich weiterhin nach deren Webseite.

Private Bereiche bieten diese Funktionen nicht an und sind im Manifest nicht für Bild-in-Bild freigegeben. Beim Öffnen eines privaten Bereichs aus dem zweiten Fenster führt das Schließen zurück zu diesem Fenster. Pro App kann ein Video schweben; ein weiteres Video beendet die laufende Wiedergabe nicht automatisch.

Gerätetests auf Android 9 und 15 prüfen mit einer echten H.264-Datei die fortlaufende Wiedergabe während des Surfens, getrennte Aufgaben und Tabs, gemeinsame normale Cookies, die nativen Bild-in-Bild-Aktionen sowie das Schließen des Videofensters ohne Verlust der anderen Sitzung. Separate Tests prüfen die privaten Manifestgrenzen und gültige Video-Seitenverhältnisse.


## Reddit-Schnellzugriff (0.4.25)

Die Startseite enthält eine eigene kompakte **Reddit**-Kachel neben YouTube mit einem lokal eingebetteten orangefarbenen Reddit-Icon. Antippen öffnet **https://www.reddit.com/**.


## Heimautomation-Schnellzugriff (0.4.26)

Die Startseite enthält eine eigene kompakte **Heimautomation**-Kachel mit lokal eingebettetem Haus-/WLAN-Icon. Sie öffnet direkt **http://192.168.178.125:8082/vis-2/index.html#Ger%C3%A4te**, einschließlich des Geräte-Ankers. Die Adresse ist im Heimnetz beziehungsweise über eine passende VPN-Verbindung erreichbar. Die Kachel lädt beim Anzeigen der Startseite keine Inhalte vom Heimautomationsserver.


## Grüne Download-Punkte direkt am Video (0.4.27)

Auf sichtbaren HTML-Videos erscheint oben rechts ein grüner Punkt mit Downloadpfeil. Antippen öffnet für genau diesen Player die vorhandene Download-Auswahl mit Vorschaubild, Auflösung, Dauer und dem Zugang zu allen verfügbaren Informationen. Mehrere deklarierte Quellen beziehungsweise Qualitätsstufen werden separat für dieses Video angeboten. Die normale Videoliste bleibt für die Suche auf der gesamten Seite erreichbar.

Die Punkte sind native Schaltflächen mit 44-dp-Trefffläche. Sie folgen Scrollen, Zoom, Größenänderungen, nachgeladenen Videos und zugänglichen eingebetteten Frames. Verdeckte beziehungsweise nicht mehr sichtbare Videos erhalten keinen Punkt; beim Tab-/Seitenwechsel, im Hintergrund und im nativen Video-Vollbild werden die Punkte entfernt. Es gibt keine zusätzliche Android-JavaScript-Schnittstelle und keine automatischen Downloads. Vorschaubilder aus dem laufenden Video werden erst beim Antippen erzeugt.

Die Zuordnung verwendet die Quellen des einzelnen Videoelements. Bei YouTube-Watch-Seiten wird der vorhandene Resolver für die identifizierte Video-ID genutzt. Blob-/MSE-Player ohne eindeutige Quelle bieten einen Hinweis und auf Wunsch die separat benannte Suche auf der gesamten Seite; sie erhalten nicht stillschweigend fremde Quellen. Der Inhalt fremder Cross-Origin-Frames bleibt getrennt; dort kann die normale Videoliste erforderlich sein. Website- und DRM-Beschränkungen werden nicht umgangen. Private Bereiche verwenden weiterhin ihren eigenen Downloadordner und Cookie-Speicher.

Prüfungen umfassen die Zuordnung mehrerer Videos, stabile Identitäten nach DOM-Änderungen, Sichtbarkeit/Überdeckung, Zoom-Viewport, eingebettete Frames und das Erzeugen von Vorschaubildern erst beim Antippen. Android-Gerätetests klicken den nativen Punkt des zweiten von zwei Videos und speichern dessen Originaldatei vollständig in einem Testordner; sie prüfen auch Scrollen, dynamische Videos, Überdeckungen sowie Tab-/Seitenwechsel.


## Sichtbare Video-Punkte auf mobilen Seiten (0.4.28)

Die Anzeige erkennt jetzt auch mobile Player, deren Bedienelemente als transparente Ebene neben dem Video liegen, und Videos in offenen Shadow Roots. Eine solche Ebene blendete den Punkt bisher aus. Verdeckt ein fremdes Element nur eine Ecke, wird eine andere freie Ecke versucht. Echte überdeckende Dialoge und verdeckte Videos bleiben ausgeschlossen. Der grüne Kreis ist größer und besitzt einen dunklen Rand für helle Videobilder.

Zusätzlich erhalten sichtbare Video-Vorschaubilder mit gültigen YouTube-Watch-/Shorts-/Live-Links und n-tv-Mediathek-Videolinks einen Punkt. Ein YouTube-Vorschaubild öffnet die Quellen seiner eigenen Video-ID über den vorhandenen Resolver, auch wenn die aktuelle Seite die Startseite ist. Ein n-tv-Vorschaubild zeigt den gewählten Film und öffnet über „Video öffnen“ seine Videoseite; eine HTML-Seite wird nicht als Videodatei angeboten. Nach dem Start des Films ist der Punkt am eigentlichen Player nutzbar. Video.js-Player liefern zusätzlich ihre deklarierten aktuellen Quellen.

Bekannte eingebettete YouTube-/RTL-/n-tv-Player können einen Punkt am Frame erhalten, auch wenn dessen Inhalt wegen einer fremden Origin nicht lesbar ist. Dieser Punkt erklärt die fehlende eindeutige Quellenzuordnung und bietet die ausdrücklich bezeichnete Videoliste der gesamten Seite an. Er gibt keine fremde Quelle als das angeklickte Video aus. Ein erfolgreicher Download hängt weiterhin von verfügbaren Quellen und Anbieterbeschränkungen ab. Die Anzeige bleibt im nativen Video-Vollbild und im Hintergrund ausgeblendet.

Regressionstests verwenden echte native Punkte über Playern mit transparenten Steuerungsebenen, speichern erneut das zweite Video bytegenau und prüfen YouTube-/n-tv-Vorschaubilder einschließlich ID-Zuordnung sowie einen mobilen Shadow-DOM-Player. Node-Tests prüfen zusätzlich fremde Domains, ungültige Links, Modalüberdeckung und isolierte eingebettete Player. Diese Fixtures ersetzen keine erfolgreiche Wiedergabe oder Quellenauflösung der Live-Anbieter.
