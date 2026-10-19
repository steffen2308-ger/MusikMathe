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

**Menü → Neuer privater Tab** öffnet den getrennten privaten Bereich. Mit **Zu normalen Tabs / Zu privaten Tabs** wechselt man zwischen den Bereichen. Ein P in der Adressleiste und eine violette Umrandung kennzeichnen private Tabs. Beim letzten privaten Tab oder **Alle privaten Tabs schließen** endet die Sitzung. Normale Tabs und Anmeldungen bleiben erhalten.

Private Tabs laufen ab Android 9 in einem nicht exportierten eigenen Prozess mit eigenem WebView-Datenverzeichnis. Vor dem ersten WebView-/CookieManager-Zugriff wird ein eventuell verbliebenes privates Profil gelöscht; schlägt dies fehl, startet der private Browser nicht. Private Adressen, Verlauf und Auswahl werden nicht gespeichert oder wiederhergestellt. Formularspeicherung und Autofill sind dort deaktiviert; Android wird um eine Tastatur ohne personalisiertes Lernen gebeten. FLAG_SECURE verhindert Screenshots der privaten Ansicht. Beim Beenden werden die WebViews zerstört, das private Verzeichnis bereinigt und der private Prozess beendet; der normale Prozess bereinigt verbliebene Dateien zusätzlich, sobald der private Prozess gestoppt ist. Bei einem erzwungenen Prozessende kann die Bereinigung erst beim nächsten Start erfolgen.

Private Tabs verbergen weder die IP-Adresse noch Aktivitäten vor Websites oder Internetanbietern. Bewusst heruntergeladene Dateien und geteilte/kopierte Inhalte bleiben erhalten. Lesezeichen sind im privaten Bereich lesbar; neue Lesezeichen werden dort nicht gespeichert. Unter Android 6–8 bleibt der normale Browser nutzbar, statt eine nur scheinbare Trennung anzubieten.

Auf der Startseite gibt es drei neue Zugänge für Deutschland:

- **Wikipedia · meistgelesen**: lokale Ansicht mit den 50 meistgelesenen Artikeln aus Deutschland (Wikimedia Analytics API, DE, alle Wikipedia-Sprachen). Das aktuelle verfügbare Tagesdatum wird angezeigt; bei fehlenden Tagesdaten werden bis zu sieben vergangene Tage geprüft. Wikipedia veröffentlicht gerundete Artikelaufrufe, keine Suchanfragen. Keine Umdeutung einer Sprachrangliste in ein Länderranking. Die Liste wird erst beim Öffnen geladen; Netzwerkfehler liefern eine Wiederholen-Ansicht, keine erfundenen Zahlen.
- **Meistbesuchte Webseiten**: [Öffentliche Semrush-Rangliste für Deutschland](https://www.semrush.com/trending-websites/de/all/) ohne Anmeldung (die führenden 20 Webseiten sind frei sichtbar).
- **Google Trends**: [Aktuelle Suchtrends für Deutschland, deutsche Oberfläche](https://trends.google.com/trending?geo=DE&hl=de).

Die Wikipedia-Ansicht basiert auf dem offiziellen [Country-Ranking-Endpunkt](https://doc.wikimedia.org/generated-data-platform/aqs/analytics-api/reference/page-views.html). Sie verwendet weder Standortfreigabe noch Browsercookies.

Zusätzlich führt GitHub Actions einen Android-9-Emulator-Test mit echter WebView aus. Ein lokaler HTTP-Testserver setzt unterschiedliche Cookies und localStorage-Werte in normalen und privaten Tabs. Der Test prüft die Trennung beim Wechseln und das Zurücksetzen nach Ende der privaten Sitzung; er benötigt keine externe Website.

## Videos erkennen und speichern (0.3)

Video auf einer Webseite starten, dann die **Videotaste** in der unteren Leiste oder **Menü → Videos erkennen und speichern** öffnen. Die Zahl an der Taste zeigt gefundene Quellen des aktuellen Tabs. Die Liste sammelt direkte MP4-/WebM-/andere Videoadressen, HLS-Manifeste (`m3u8`) und DASH-Manifeste (`mpd`) aus WebView-Anfragen, HTML5-Videoelementen und den Ressourcen der Seite. Nach dem Laden der Seite beobachtet ein seiteneigenes JavaScript außerdem Content-Type-Header nachfolgender fetch-/XHR-Antworten, ohne deren Inhalt zu lesen oder eine JavaScript-Schnittstelle zu Android freizugeben. So werden auch manche Quellen ohne Video-Dateiendung gefunden. Gleichnamige Adressen werden zusammengefasst, Segmentdateien möglichst ausgefiltert. Die Kandidaten stehen nur im Arbeitsspeicher und werden bei einem Seitenwechsel gelöscht. Einige Player verbergen ihre Quellen, insbesondere hinter Service Workern oder individuellen POST-Anfragen. Eine universelle Erkennung ist nicht möglich.

Eine Quelle antippen und **Als MP4 speichern** wählen; direkte Dateien lassen sich alternativ unverändert im Originalformat speichern. Beim ersten Video wird ein Downloadordner gewählt und dauerhaft freigegeben. Danach werden neue Dateien automatisch dort angelegt; gleiche Dateinamen erhalten einen zusätzlichen Zähler. Der Ordner kann unter **Menü → Einstellungen → Video-Downloadordner** geändert werden. Media3 führt bei unterstützten Quellen Ton und Bild in einer MP4-Datei zusammen; falls nötig werden kompatible H.264-/AAC-Spuren erzeugt. Adaptive Quellen verwenden standardmäßig die höchste unterstützte Variante. Für lange oder Live-Quellen gibt es zusätzlich zeitlich begrenzte Ausschnitte. Je nach Live-Zeitleiste funktioniert ein Export nicht oder enthält den verfügbaren Ausschnitt des Streamfensters; keine universelle Live-Aufnahme-Zusage. DRM-/Lizenzschutz, abgelaufene Quellen, fehlende Berechtigungen und nicht unterstützte Codecs werden nicht umgangen. Kopiergeschützte Streams können erkannt werden, der Export schlägt dann jedoch fehl.

Die Exporte laufen als Android-Vordergrunddienst mit Fortschritt. Mehrere Videos lassen sich vormerken und werden je Browserbereich in ihrer Reihenfolge nacheinander gespeichert. Unter **Menü → Einstellungen → Video-Downloads** oder **Videos → Download-Fortschritt** stehen der aktuelle und die wartenden Downloads; **Abbrechen** überspringt in dieser Ansicht den laufenden Download, **Alle abbrechen** leert die Warteschlange. Fehler blockieren die folgenden Downloads nicht. Die Ansicht aktualisiert sich automatisch und zeigt Dateiname, aktuellen Schritt, Prozent (soweit verfügbar), übertragene Datenmenge, durchschnittliche Geschwindigkeit sowie eine Restzeitschätzung für den laufenden Schritt. Herunterladen/MP4-Erstellung und das abschließende Kopieren in den Zielordner werden getrennt angezeigt; erst nach dem Schließen der Zieldatei gilt ein Video als gespeichert. Bei unbekannter Größe oder Live-Streams bleiben Prozent beziehungsweise Restzeit gegebenenfalls offen. Die Restzeit gilt nicht für die gesamte Warteschlange. Bei längerem Stillstand wird die alte Schätzung ausgeblendet. Diese Ansicht funktioniert auch ohne Benachrichtigungsfreigabe. Der Fortschritt steht außerdem in der Systembenachrichtigung; Antippen öffnet dieselbe Live-Ansicht; Android kann deren Anzeige abhängig von den Benachrichtigungseinstellungen begrenzen. HLS/DASH-Exporte benötigen vorübergehend freien internen Speicher für die MP4-Datei, bevor sie zum gewählten Ziel kopiert wird. Abbruch oder Fehler entfernen die temporäre Datei und versuchen die neu erzeugte Zieldatei zu löschen.

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

Nach der erstmaligen Wahl des Videoordners wird jedes weitere Video direkt vorgemerkt. Die Benachrichtigung zeigt den Fortschritt und die Anzahl wartender Downloads. Die Warteschlange bleibt im Arbeitsspeicher, auch beim Wechsel der Webseite; nach einem Prozessabbruch wird sie nicht wiederhergestellt. Normale und private Warteschlangen sind getrennt.

Service-Tests prüfen mit echten lokalen HTTP-Antworten und getrennten Dokumentzielen die Reihenfolge, das Weitermachen nach HTTP-Fehlern, Abbruch des aktuellen Downloads, Leeren der Warteschlange und verspätete Rückmeldungen abgebrochener Downloads.

## BestBrowser und dauerhafter Videoordner (0.4.3)

Die App heißt BestBrowser. Paketname und Signierung bleiben für Updates sowie vorhandene Tabs, Lesezeichen und verschlüsselte Passwörter erhalten; das GitHub-Repository bleibt MusikMathe. Der Videoordner wird auch nach einem Neustart wiederverwendet und gilt in beiden Browserbereichen. Eine Änderung unter Menü → Einstellungen gilt für neue Downloads; bereits vorgemerkte Dateien behalten ihr Ziel. Normale Datei-Downloads über Androids Downloadmanager bleiben im systemweiten Downloads-Ordner.

Ist der gewählte Videoordner nicht mehr erreichbar oder beschreibbar, wird keine Datei überschrieben und die App bietet eine neue Ordnerwahl an. Tests prüfen die Wiederverwendung nach einem App-Neustart, neue Dateinamen bei Kollisionen, Ordnerwechsel mit bereits angelegten Zielen und fehlgeschlagenen Schreibzugriff.

## Werbeblocker und neues Browser-Icon (0.4.4)

Der eingebaute Werbeblocker ist zunächst eingeschaltet. **Menü → Werbeblocker** schaltet ihn direkt ein oder aus; unter **Menü → Einstellungen → Werbeblocker** gibt es denselben Schalter mit einer kurzen Erklärung. Beim Umschalten wird die aktuelle Seite neu geladen. Die Einstellung gilt für neue Anfragen in allen Tabs des jeweiligen Browserbereichs. Bereits geladene Inhalte in anderen Tabs ändern sich erst nach dem Neuladen dieser Seiten.

Normale Tabs behalten die Auswahl nach einem App-Neustart. Eine neue private Sitzung übernimmt die normale Einstellung; Änderungen im privaten Bereich bleiben nur für diese Sitzung im Arbeitsspeicher und verändern die normale Einstellung nicht.

Der Blocker prüft HTTP-/HTTPS-Unteranfragen gegen eine kleine, mitgelieferte Liste bekannter Werbe- und Trackingserver. Er lädt keine Filterlisten herunter, speichert keine besuchten Adressen und blockiert keine ausdrücklich aufgerufene Hauptseite. Treffer erhalten eine leere, nicht zwischenspeicherbare Antwort, bevor sie an die Videoerkennung gelangen. Die Liste wird mit App-Updates gepflegt. Das ist kein vollständiger EasyList-/uBlock-Filter: Werbung aus derselben Quelle wie der Seiteninhalt, viele Videoanzeigen einschließlich YouTube und Anfragen außerhalb der WebView-Abfangfunktion werden damit nicht zuverlässig entfernt. Falls eine Website nicht funktioniert, den Blocker ausschalten und die Seite neu laden. Chrome-Erweiterungen werden weiterhin nicht ausgeführt.

Das neue Launcher-Icon verbindet einen mehrfarbigen Ring, eine blaue Mitte, ein Löwen-/Fuchsmotiv und einen orangefarbenen Schweif. Android 6 und 7 verwenden ein klassisches Drawable; ab Android 8 passt sich das adaptive Icon der Launcher-Form an. Paketname und Entwicklungsschlüssel bleiben erhalten, sodass die APK als Update installierbar ist.

Neue Tests prüfen Domain-Grenzen, Unterdomains, Groß-/Kleinschreibung, erlaubte Hauptnavigation, das Ausschalten, Wiederherstellung und private Sitzungseinstellungen sowie die tatsächliche WebView-Request-Abfangfunktion in mehreren Tabs. Pull Requests führen jetzt ebenfalls den vorhandenen APK-, Test- und Lint-Workflow aus.

## Erweiterter Werbeblocker und VPN-Zugänge (0.4.5)

Der Werbeblocker enthält jetzt HaGeZi Multi LIGHT mit 65.249 mitgelieferten Servereinträgen sowie rund 13.600 allgemeinen und 8.800 seitenspezifischen EasyList-CSS-Regeln einschließlich Ausnahmen. Die normalen CSS-Regeln greifen auch bei nachträglich eingefügten Werbeelementen. Bei aktuellen WebViews werden sie am Dokumentanfang eingerichtet; ältere Provider verwenden Seiten-Callbacks. Nicht unterstützte prozedurale Filter, Scriptlets und vollständige EasyList-Netzwerksyntax sind nicht implementiert. Regeln innerhalb fremder Frames, restriktive Content-Security-Policy in älteren WebViews, Service Worker und eingebettete Videoanzeigen können die Filterung begrenzen. Eine zuverlässige YouTube-Videoanzeigen-Sperre wird weiterhin nicht zugesagt.

**Menü → Werbeblocker** öffnet den Ein-/Ausschalter, eine Ausnahme für die aktuelle Website und einen Zähler für abgefangene Anfragen. Eine Ausnahme gilt für den angezeigten Host und seine Unterdomains; im normalen Browser bleibt sie gespeichert, in privaten Tabs nur für die laufende Sitzung. **Serverliste aktualisieren** lädt ausschließlich die öffentliche HaGeZi-Domainliste über HTTPS, ohne Browsercookies oder besuchte Adressen zu senden. Fehlerhafte, zu große oder unvollständige Antworten ersetzen die bisherige Liste nicht. Die gültige Liste wird atomar unter no_backup gespeichert. EasyList-CSS-Regeln werden mit App-Updates aktualisiert. Quelle und Lizenzstände liegen unter filters/; alle Filter arbeiten auch ohne vorherigen Download.

Abgefangene Netzwerkressourcen verwenden einen fehlschlagenden Eingabestrom statt einer erfolgreichen leeren HTTP-204-Antwort. Das verhindert, dass Fetch-/HEAD-Anfragen so aussehen, als hätten sie den Werbeserver erreicht. Manche WebViews oder Testseiten können blockierte Anfragen dennoch anders bewerten. Die CSS-Prüfungen der vom Nutzer genannten Testseite adblock.turtlecute.org sind mit zwei transparent dokumentierten Website-Regeln berücksichtigt; weder Messwerte noch Test-Anzeigen werden manipuliert. Der Testwert allein ist kein Nachweis der gesamten Filterabdeckung.

Unter **Menü → VPN** gibt es beide Wege: **Proton VPN Free** als kostenlosen Anbieter und **vorhandene VPN-Zugänge** über WireGuard, OpenVPN Connect oder Androids VPN-Einstellungen. Die jeweilige echte VPN-App baut den Tunnel auf; BestBrowser nutzt die Android-VPN-Verbindung. Fehlt die App, wird ihre konkrete Play-Store-Seite geöffnet, alternativ die offizielle Anbieterseite in einem neuen Tab. BestBrowser enthält selbst keinen VPN-Tunnel, stellt keinen Server bereit und speichert keine VPN-Schlüssel oder Anmeldedaten. Es werden weder Konten angelegt noch kostenpflichtige Optionen gebucht. Bei Proton ist ein eigenes Konto nötig. Eine externe App kann das gesamte Gerät schützen; Split-Tunneling-Einstellungen können BestBrowser ausschließen.

Der VPN-Dialog zeigt ausschließlich, ob Android für den Aufrufer eine VPN-Verbindung meldet. Unbekannte oder fehlende Zustände werden entsprechend angezeigt. Ein erkannter VPN-Transport beweist weder einen erreichbaren VPN-Server noch eine andere öffentliche IP-Adresse; dafür gibt es den bewusst auswählbaren Link zur IP-Prüfung. Ein kostenloses VPN ersetzt keine Werbefilter.

Tests umfassen native WebView-Fetch-/HEAD-Fehler, wirklich ausgeführte bzw. gesperrte Werbeskripte, statische und dynamische CSS-Werbeelemente, Positivkontrollen für normale Inhalte, Website-Ausnahmen und Ausschalten, Filterdaten-Validierung sowie die ehrliche VPN-Statusanzeige. Die kontrollierten WebView-Prüfungen benötigen keine externen Websites und laufen auf Android 9 und Android 15. Ein bestimmter Live-Test-Prozentwert oder eine echte VPN-Verbindung ohne Nutzerzugang wird dadurch nicht bestätigt.


## Doppeltipp-Vollbild (0.4.6)

Ein Doppeltipp beziehungsweise Doppelklick auf eine freie Fläche der Webseite oder der Browserleiste schaltet den Vollbildmodus ein. Browserleisten und Android-Systemleisten werden ausgeblendet. Erneutes Doppeltippen auf freie Fläche oder die Zurück-Taste beendet den Browser-Vollbildmodus, ohne dabei im Verlauf zurückzugehen. Die Android-Leisten lassen sich im Vollbild vorübergehend vom Bildschirmrand einblenden. Alternativ ist der Modus über Menü → Vollbild erreichbar.

Links, Schaltflächen, Formulare, Medien, Text und andere erkennbare Bedienelemente lösen den Modus nicht aus. Die Prüfung erfolgt an beiden Berührungspositionen im aktuellen Dokument; es wird keine JavaScript-Schnittstelle mit nativen Vollbildrechten auf Webseiten veröffentlicht. Scrollen, Pinch-Zoom und lange Berührungen bleiben erhalten. Der Modus funktioniert in normalen und privaten Tabs. Video-Vollbild bleibt getrennt: Nach dem Schließen des Videos wird der vorherige Browser-Vollbildzustand wiederhergestellt.


## Vorschläge aus dem Verlauf (0.4.8)

Beim Tippen in die Adressleiste erscheinen bis zu acht passende besuchte Seiten mit Titel und genauer Webadresse. Der Abgleich berücksichtigt Adresse und Seitentitel ohne Unterscheidung von Groß- und Kleinschreibung sowie mehrere Suchwörter. Adressanfänge stehen vor anderen Treffern; innerhalb einer Treffergruppe stehen zuletzt besuchte Seiten oben. Antippen öffnet die gespeicherte Adresse. Bei angeschlossener Tastatur wählen die Pfeiltasten einen Vorschlag, Enter öffnet ihn. Ohne ausgewählten Vorschlag öffnet Enter die eingegebene Adresse beziehungsweise die gewählte Suchmaschine.

Die Vorschläge werden ausschließlich lokal ermittelt. Tippen löst keine Webseitenaufrufe oder Abfragen bei einem Vorschlagsdienst aus. Leere Eingaben zeigen keine Vorschläge. Gelöschte Verlaufseinträge werden nicht weiter angeboten. Private Tabs lesen keinen normalen Verlauf und legen weiterhin keinen eigenen Verlauf an; dort erscheinen daher keine Verlaufsvorschläge.


## Dezentes Icon auf der Startseite (0.4.9)

Die lokale Startseite zeigt das vorhandene Browser-Icon als großes, blasses Hintergrundmotiv. Die Deckkraft beträgt 7 Prozent im hellen und 9 Prozent im dunklen Modus. Das Motiv bleibt hinter dem Inhalt und fängt keine Berührungen ab. Es verwendet die bereits enthaltene Android-Bildressource, ohne zusätzliche Netzwerkabfrage.


## Vollbild bis zum Bildschirmrand (0.4.10)

Browser- und Video-Vollbild nutzen auch die Bildschirmfläche um den Kameraausschnitt. Dafür wird Android ab Version 9 das Zeichnen in diesem Bereich erlaubt; ab Android 11 gilt dies für alle Bildschirmränder. Im Vollbild fügt die App keinen Abstand für den Kameraausschnitt hinzu. Der Abstand für eine eingeblendete Bildschirmtastatur bleibt erhalten. Nach dem Verlassen des Vollbilds werden Browserleisten und ihre Schutzabstände wiederhergestellt.
