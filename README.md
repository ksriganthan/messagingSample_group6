# Problemfall 3 – Auftrags-Dispositionssystem via Messaging (Gruppe 6)

## 1. Projektübersicht

Dieses Projekt implementiert ein verteiltes **Auftrags-Dispositionssystem** auf Basis von
**Apache ActiveMQ** (Message Broker) und **Spring Boot JMS** (Java Message Service).

Eine zentrale **Disposition** (MessagePublisher) erstellt Aufträge manuell über eine GUI und
verteilt diese über einen Message Broker an **Clients (Arbeiter)** (MessageConsumer).
Die Aufträge werden dabei nicht nur auf einem allgemeinen Topic veröffentlicht, sondern zusätzlich
auf **regionsspezifischen** und **typspezifischen** Topics (Content-Based Router).
Clients können Aufträge über die Topic-Wahl und/oder clientseitige Filter nach Region und
Auftragstyp filtern und senden automatisch eine Zuweisungsanfrage. Die Disposition entscheidet
**manuell** über Zuweisung oder Ablehnung. Bei einer Zuweisung werden alle weiteren Anfragen
für denselben Auftrag automatisch abgelehnt, sodass keine Doppelvergabe möglich ist.

### Module

| Modul | Rolle | Beschreibung |
|---|---|---|
| **MessagePublisher** | Disposition | Erstellt Aufträge per GUI und veröffentlicht sie auf Haupt- und Content-Based-Router-Topics. Empfängt Zuweisungsanfragen und entscheidet manuell über Zuweisung/Ablehnung. |
| **MessageConsumer** | Client / Arbeiter | Empfängt Aufträge vom Broker, filtert per Topic-Wahl (Content-Based Router) und/oder clientseitig nach Region und Typ, fragt automatisch eine Zuweisung an. |

---

## 2. Architektur und Nachrichtenfluss

### 2.1 Warum ein Message Broker?

In einem verteilten System kommunizieren Komponenten nicht direkt miteinander, sondern über
einen **Message Broker** als zentrale Vermittlungsstelle. Das hat folgende Vorteile:

- **Entkopplung**: Publisher und Consumer kennen sich nicht gegenseitig. Sie kennen nur den Broker
  und die Channel-Namen.
- **Skalierbarkeit**: Es können beliebig viele Clients hinzugefügt werden, ohne den Publisher
  anzupassen.
- **Zuverlässigkeit**: Der Broker puffert Nachrichten und stellt die Zustellung sicher.

### 2.2 Topics vs. Queues

Wir verwenden zwei unterschiedliche Messaging-Patterns, je nach Anwendungsfall:

**Topics (Publish/Subscribe)** – Eine Nachricht wird an **alle** Subscriber verteilt.
Das verwenden wir für die Verteilung von Aufträgen und Zuweisungsantworten, da alle Clients
diese Informationen erhalten sollen.

**Queues (Point-to-Point)** – Eine Nachricht wird an **genau einen** Empfänger zugestellt.
Das verwenden wir für Zuweisungsanfragen, da jede Anfrage genau einmal von der Disposition
verarbeitet werden soll.

### 2.3 Gesamtfluss

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    ActiveMQ Broker (192.168.111.6:61616)                    │
│                                                                            │
│  Topics (Content-Based Router):          Queue:                            │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────┐   │
│  │ group6.dispo.jobs.new        │  │ group6.dispo.jobs.               │   │
│  │ group6.dispo.jobs.new.basel  │  │        requestAssignment         │   │
│  │ group6.dispo.jobs.new.zuerich│  └──────────────▲───────────────────┘   │
│  │ group6.dispo.jobs.new.bern   │                 │                       │
│  │ group6.dispo.jobs.new.repair │  Topic:         │                       │
│  │ group6.dispo.jobs.new.       │  ┌──────────────┴───────────────────┐   │
│  │        maintenance           │  │ group6.dispo.jobs.assignments    │   │
│  └──────────▲───────────────────┘  └──────────────┬───────────────────┘   │
│             │                                     │                       │
└─────────────┼─────────────────────────────────────┼───────────────────────┘
              │                                     │
   ┌──────────┼─────────────────────────────────────┼──────────┐
   │          │                                     │          │
   │  SCHRITT 1                    SCHRITT 3        ▼          │
   │  Disponent erstellt           Disponent entscheidet       │
   │  Auftrag in GUI und          manuell: Zuweisen            │
   │  veröffentlicht auf          oder Ablehnen                │
   │  mehreren Topics             (auto-Ablehnung bei          │
   │  (Content-Based Router)       Doppelvergabe)              │
   │         │            ┌──────────────────┐                 │
   │         │            │   Publisher       │                 │
   │         │            │  (Disposition)    │                 │
   │         │            └────────▲─────────┘                 │
   │         │                     │                           │
   │         │              SCHRITT 2                          │
   │         │              Consumer sendet                    │
   │         │              Anfrage auf Queue                  │
   │         │                     │                           │
   │         ▼                     │        SCHRITT 4          │
   │  ┌────────────────────────────┴──┐     Consumer empfängt  │
   │  │        Consumer               │     Bestätigung oder   │
   │  │     (Client / Arbeiter)       │◄─── Ablehnung          │
   │  └───────────────────────────────┘                        │
   └───────────────────────────────────────────────────────────┘

Schritt 1: Disponent erstellt einen Auftrag in der GUI (Region, Typ, Beschreibung).
           Der Auftrag wird auf dem Haupt-Topic + regionsspezifischem + typspezifischem
           Topic veröffentlicht (Content-Based Router).
Schritt 2: Client empfängt den Auftrag, filtert nach Region + Typ, sendet automatisch
           eine Zuweisungsanfrage an die Queue.
Schritt 3: Disponent sieht die Anfrage in der GUI-Tabelle und entscheidet manuell:
           Zuweisen oder Ablehnen. Bei Zuweisung werden alle weiteren Anfragen für
           denselben Job automatisch abgelehnt.
Schritt 4: Client empfängt die Antwort und zeigt das Ergebnis an.
```

---

## 3. Channels (Topics & Queues)

### 3.1 Namenskonvention

Gemäss den Vorgaben des Dozenten müssen alle eigenen Topics und Queues mit dem **Gruppennamen
gefolgt von einem Punkt** beginnen. Da wir Gruppe 6 sind, verwenden wir das Prefix `group6.`.
Damit sind unsere Channels klar von anderen Gruppen und dem Fremdsystem des Dozenten getrennt.

### 3.2 Channel-Übersicht

| Channel-Name | Typ | Richtung | Nachrichtentyp | Beschreibung |
|---|---|---|---|---|
| `group6.dispo.jobs.new` | **Topic** | Disposition → Clients | `JobMessage` | **Alle** neuen Aufträge |
| `group6.dispo.jobs.new.basel` | **Topic** | Disposition → Clients | `JobMessage` | Nur Aufträge Region **Basel** (Content-Based Router) |
| `group6.dispo.jobs.new.zuerich` | **Topic** | Disposition → Clients | `JobMessage` | Nur Aufträge Region **Zuerich** (Content-Based Router) |
| `group6.dispo.jobs.new.bern` | **Topic** | Disposition → Clients | `JobMessage` | Nur Aufträge Region **Bern** (Content-Based Router) |
| `group6.dispo.jobs.new.repair` | **Topic** | Disposition → Clients | `JobMessage` | Nur **Reparatur**-Aufträge (Content-Based Router) |
| `group6.dispo.jobs.new.maintenance` | **Topic** | Disposition → Clients | `JobMessage` | Nur **Wartungs**-Aufträge (Content-Based Router) |
| `group6.dispo.jobs.requestAssignment` | **Queue** | Client → Disposition | `JobRequestMessage` | Clients senden Zuweisungsanfragen |
| `group6.dispo.jobs.assignments` | **Topic** | Disposition → Clients | `JobAssignmentMessage` | Zuweisungsantworten (zugewiesen/abgelehnt) |

### 3.3 Warum Topic für Aufträge und Antworten, aber Queue für Anfragen?

- **Aufträge (Topic)**: Alle Clients sollen jeden neuen Auftrag sehen. Ein Topic stellt sicher,
  dass die Nachricht an alle Subscriber gleichzeitig zugestellt wird.

- **Zuweisungsanfragen (Queue)**: Jede Anfrage soll **genau einmal** verarbeitet werden.
  Die Queue stellt sicher, dass jede Anfrage nur einmal bearbeitet wird.

- **Zuweisungsantworten (Topic)**: Die Antwort wird über ein Topic verteilt, damit alle Clients
  sie empfangen. Jeder Client filtert selbst, ob die Antwort an ihn gerichtet ist (anhand der `clientId`).

### 3.4 Content-Based Router

Ein zentrales Architekturmuster in unserem System ist der **Content-Based Router** (EIP-Pattern).
Beim Veröffentlichen eines Auftrags sendet die Disposition die Nachricht nicht nur auf das
allgemeine Topic, sondern zusätzlich auf regionsspezifische und typspezifische Topics:

```
Auftrag: region=basel, type=repair
  → group6.dispo.jobs.new              (alle Clients)
  → group6.dispo.jobs.new.basel        (nur Basel-Clients)
  → group6.dispo.jobs.new.repair       (nur Repair-Clients)
```

Dadurch können Clients über die **Topic-Wahl** bereits auf Broker-Ebene festlegen, welche
Aufträge sie empfangen möchten. Die Filterung findet also nicht erst beim Client statt, sondern
wird durch die Kanalstruktur der Topics ermöglicht. Dies ermöglicht eine **lose Kopplung**
der Komponenten, eine **flexible Erweiterbarkeit** sowie eine **parallele Verarbeitung** von Aufträgen.

---

## 4. Filterung – Zwei Varianten

Clients können Aufträge auf **zwei Wegen** filtern, die sich auch kombinieren lassen:

### Variante 1: Content-Based Router (Topic-Wahl)

Der Client abonniert ein spezifisches Topic und erhält nur die dafür bestimmten Aufträge.
Die Filterung geschieht auf **Broker-Ebene** über die Wahl des Topics in `application.properties`:

| Topic | Was kommt an? |
|---|---|
| `group6.dispo.jobs.new` | Alle Aufträge |
| `group6.dispo.jobs.new.basel` | Nur Region Basel |
| `group6.dispo.jobs.new.zuerich` | Nur Region Zuerich |
| `group6.dispo.jobs.new.bern` | Nur Region Bern |
| `group6.dispo.jobs.new.repair` | Nur Reparaturen |
| `group6.dispo.jobs.new.maintenance` | Nur Wartungen |

### Variante 2: Clientseitiger Filter

Der Client abonniert das allgemeine Topic (`group6.dispo.jobs.new`) und filtert die empfangenen
Nachrichten über die Properties `client.region` und `client.jobType`.
Der Region-Filter unterstützt **komma-getrennte Mehrfachwerte** (z.B. `basel,zuerich`).
Leer bedeutet, dass alle Regionen akzeptiert werden.

### Kombination beider Varianten

Beide Varianten lassen sich sinnvoll kombinieren. Beispiel:

```properties
# Topic für Basel + zusätzlich nach Typ filtern → nur Basel-Reparaturen
channel.topic.newJobs=group6.dispo.jobs.new.basel
client.jobType=repair
```

> **Achtung**: Nicht denselben Filter widersprüchlich setzen!
> z.B. Topic `...new.basel` + `client.region=zuerich` → es kommt **nichts** an.
>
> Wenn ein regionsspezifisches oder typspezifisches Topic gewählt wird, den gleichartigen
> clientseitigen Filter **leer lassen**. Der jeweils andere Filter kann weiterhin gesetzt werden.

---

## 5. Nachrichtentypen

Alle Nachrichten werden als **JSON** über den Broker übertragen. Die Umwandlung zwischen
Java-Objekten und JSON erfolgt automatisch über den `MappingJackson2MessageConverter`.

### 5.1 JobMessage – Neuer Auftrag

```json
{
  "jobId": "JOB-0001",
  "description": "Reparatur an Heizungsanlage",
  "region": "basel",
  "jobType": "repair"
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | Eindeutige Auftrags-ID (z.B. `JOB-0001`) |
| `description` | String | Beschreibung des Auftrags |
| `region` | String | Region (`basel`, `zuerich`, `bern`) |
| `jobType` | String | Typ (`repair` = Reparatur, `maintenance` = Wartung) |

### 5.2 JobRequestMessage – Zuweisungsanfrage

```json
{
  "jobId": "JOB-0001",
  "clientId": "group6_consumer"
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des gewünschten Auftrags |
| `clientId` | String | ID des anfragenden Clients |

### 5.3 JobAssignmentMessage – Zuweisungsantwort

```json
{
  "jobId": "JOB-0001",
  "clientId": "group6_consumer",
  "assigned": true
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des angefragten Auftrags |
| `clientId` | String | ID des anfragenden Clients |
| `assigned` | boolean | `true` = zugewiesen, `false` = abgelehnt (bereits vergeben oder manuell abgelehnt) |

---

## 6. Disposition (MessagePublisher)

### 6.1 Aufgabe

Der Publisher ist die zentrale Disposition mit drei Verantwortlichkeiten:

1. **Aufträge manuell erstellen und veröffentlichen**: Über die GUI erstellt der Disponent einen
   Auftrag (Job-ID, Beschreibung, Region, Typ). Beim Veröffentlichen wird der Auftrag auf dem
   Haupt-Topic sowie auf den regionsspezifischen und typspezifischen Topics publiziert
   (Content-Based Router).

2. **Zuweisungsanfragen empfangen**: Der Publisher hört auf die Queue
   `group6.dispo.jobs.requestAssignment`. Eingehende Anfragen erscheinen in einer Tabelle in der GUI.

3. **Manuell über Zuweisung entscheiden**: Der Disponent wählt eine Anfrage aus und klickt
   «Zuweisen» oder «Ablehnen». Bei Zuweisung werden alle weiteren Anfragen für denselben
   Auftrag **automatisch abgelehnt** (Duplikatsprüfung via `ConcurrentHashMap`).

### 6.2 Duplikatsprüfung & Auto-Ablehnung

Die Disposition führt eine Thread-sichere Map aller vergebenen Aufträge (`JobId → ClientId`).
Bei Zuweisung wird `putIfAbsent()` verwendet – ist der Auftrag bereits vergeben, wird
automatisch abgelehnt. Zusätzlich werden alle weiteren Anfragen in der Tabelle für denselben
Job sofort entfernt und per Ablehnungsnachricht beantwortet.

### 6.3 GUI (Publisher)

- **Oben**: Formular zum Erstellen von Aufträgen (Job-ID automatisch, Beschreibung, Region-Dropdown, Typ-Dropdown, «Veröffentlichen»-Button)
- **Mitte**: Tabelle mit eingehenden Zuweisungsanfragen (Job-ID, Client-ID, Zeitpunkt) + Buttons «Zuweisen» / «Ablehnen»
- **Unten**: Protokoll-Log aller Ereignisse

### 6.4 JMS-Konfiguration (Publisher)

| Bean | Typ | Zweck |
|---|---|---|
| `topicJmsTemplate` | JmsTemplate | Sendet Nachrichten an **Topics** (Aufträge + Antworten) |
| `queueFactory` | ListenerContainerFactory | Empfängt Nachrichten von **Queues** (Zuweisungsanfragen) |
| `jacksonJmsMessageConverter` | MessageConverter | Wandelt Java-Objekte ↔ JSON um |

---

## 7. Client / Arbeiter (MessageConsumer)

### 7.1 Aufgabe

Der Consumer ist der Client / Arbeiter mit drei Verantwortlichkeiten:

1. **Aufträge empfangen**: Hört auf das konfigurierte Topic (allgemein oder spezifisch per
   Content-Based Router).

2. **Aufträge filtern**: Clientseitig werden zwei konfigurierbare Filter angewendet:
   - **Region-Filter** (`client.region`): Komma-getrennt möglich, z.B. `basel,zuerich`. Leer = alle.
   - **JobType-Filter** (`client.jobType`): `repair` oder `maintenance`. Leer = alle.

3. **Zuweisung anfragen und Ergebnis anzeigen**: Für jeden akzeptierten Auftrag wird automatisch
   eine Zuweisungsanfrage an die Queue gesendet. Die Antwort (zugewiesen/abgelehnt) wird im Log angezeigt.

### 7.2 GUI (Consumer)

- Einfaches Log-Fenster mit allen empfangenen Aufträgen, Zuweisungsanfragen und Antworten
- Titel zeigt aktive Filter an: `"Auftrags-Client [group6_consumer] - Clientseitige-Filterung: Region: alle | Typ: alle"`

### 7.3 JMS-Konfiguration (Consumer)

| Bean | Typ | Zweck |
|---|---|---|
| `queueJmsTemplate` | JmsTemplate | Sendet Nachrichten an **Queues** (Zuweisungsanfragen) |
| `topicFactory` | ListenerContainerFactory | Empfängt Nachrichten von **Topics** (Aufträge + Antworten) |
| `jacksonJmsMessageConverter` | MessageConverter | Wandelt Java-Objekte ↔ JSON um |

Die JMS-Konfiguration ist **spiegelverkehrt** zum Publisher: Der Publisher sendet auf Topics
und empfängt von Queues, der Consumer sendet auf Queues und empfängt von Topics.

---

## 8. Konfiguration

Alle konfigurierbaren Werte befinden sich in den `application.properties`-Dateien.

### 8.1 Publisher (MessagePublisher)

```properties
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=admin
spring.activemq.password=admin

channel.topic.newJobs=group6.dispo.jobs.new
channel.queue.requestAssignment=group6.dispo.jobs.requestAssignment
channel.topic.assignments=group6.dispo.jobs.assignments
```

### 8.2 Consumer (MessageConsumer)

```properties
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=admin
spring.activemq.password=admin

client.id=group6_consumer

channel.queue.requestAssignment=group6.dispo.jobs.requestAssignment
channel.topic.assignments=group6.dispo.jobs.assignments

# --- Topic-Wahl (Content-Based Router) ---
# Alle:        group6.dispo.jobs.new
# Nur Basel:   group6.dispo.jobs.new.basel
# Nur Zuerich: group6.dispo.jobs.new.zuerich
# Nur Bern:    group6.dispo.jobs.new.bern
# Nur Repair:  group6.dispo.jobs.new.repair
# Nur Wartung: group6.dispo.jobs.new.maintenance
channel.topic.newJobs=group6.dispo.jobs.new

# --- Clientseitige Filter (leer = alle) ---
client.region=
client.jobType=
```

### 8.3 Filteroptionen

#### Region-Filter (clientseitig)

| Wert | Verhalten |
|---|---|
| `client.region=` | Alle Regionen |
| `client.region=basel` | Nur Basel |
| `client.region=zuerich` | Nur Zuerich |
| `client.region=bern` | Nur Bern |
| `client.region=basel,zuerich` | Basel und Zuerich |

#### JobType-Filter (clientseitig)

| Wert | Verhalten |
|---|---|
| `client.jobType=` | Alle Typen |
| `client.jobType=repair` | Nur Reparaturen |
| `client.jobType=maintenance` | Nur Wartungen |

#### Kombinationsbeispiele

```properties
# Alle Aufträge, keine Filter
channel.topic.newJobs=group6.dispo.jobs.new
client.region=
client.jobType=

# Nur Basel (Content-Based Router)
channel.topic.newJobs=group6.dispo.jobs.new.basel
client.region=
client.jobType=

# Nur Reparaturen aus Basel (Router + clientseitiger Filter)
channel.topic.newJobs=group6.dispo.jobs.new.basel
client.region=
client.jobType=repair

# Basel und Bern, nur Wartung (clientseitig)
channel.topic.newJobs=group6.dispo.jobs.new
client.region=basel,bern
client.jobType=maintenance
```

---

## 9. Starten der Anwendung

### 9.1 Voraussetzungen

- Java 11+ (getestet mit OpenJDK 25)
- Maven
- ActiveMQ Broker erreichbar unter `192.168.111.6:61616`

### 9.2 Reihenfolge

1. ActiveMQ Broker muss laufen (`192.168.111.6:61616`)
2. **MessagePublisher** starten (Disposition)
3. **MessageConsumer** starten (Client / Arbeiter)

Der Publisher sollte **zuerst** gestartet werden, da Topics nur Nachrichten an aktive Subscriber liefern.

### 9.3 Aus IntelliJ IDEA

1. **MessagePublisher**: `MessageApplication.java` → Rechtsklick → Run
2. **MessageConsumer**: `MessageConsumerApplication.java` → Rechtsklick → Run

### 9.4 Aus der Kommandozeile (Maven)

```bash
# Terminal 1: Publisher (Disposition) starten
cd MessagePublisher
mvn spring-boot:run

# Terminal 2: Consumer (Client) starten
cd MessageConsumer
mvn spring-boot:run
```

---

## 10. Projektstruktur

```
messagingSample_group6/
│
├── MessagePublisher/                  (Disposition)
│   ├── pom.xml
│   └── src/main/
│       ├── java/ch/fhnw/digi/demo/
│       │   ├── MessageApplication.java        # Spring Boot Einstiegspunkt
│       │   ├── Publisher.java                 # Aufträge veröffentlichen + Anfragen verarbeiten
│       │   ├── JmsConfig.java                 # JMS Topic/Queue Konfiguration
│       │   ├── SimpleUi.java                  # Swing GUI (Aufträge erstellen, Anfragen bearbeiten, Log)
│       │   ├── JobMessage.java                # Nachrichtentyp: Auftrag
│       │   ├── JobRequestMessage.java         # Nachrichtentyp: Zuweisungsanfrage
│       │   └── JobAssignmentMessage.java      # Nachrichtentyp: Zuweisungsantwort
│       └── resources/
│           └── application.properties         # Broker + Channel Konfiguration
│
├── MessageConsumer/                   (Client / Arbeiter)
│   ├── pom.xml
│   └── src/main/
│       ├── java/ch/fhnw/digi/demo/
│       │   ├── MessageConsumerApplication.java # Spring Boot Einstiegspunkt
│       │   ├── Receiver.java                   # Aufträge empfangen + filtern + Zuweisung anfragen
│       │   ├── JmsConfig.java                  # JMS Topic/Queue Konfiguration
│       │   ├── SimpleUi.java                   # Swing GUI (Log + Filter-Anzeige)
│       │   ├── JobMessage.java                 # Nachrichtentyp: Auftrag
│       │   ├── JobRequestMessage.java          # Nachrichtentyp: Zuweisungsanfrage
│       │   └── JobAssignmentMessage.java       # Nachrichtentyp: Zuweisungsantwort
│       └── resources/
│           └── application.properties          # Broker + Client + Filter Konfiguration
│
└── README.md                          (Diese Datei)
```

---

## 11. Technologien

| Technologie | Version | Zweck |
|---|---|---|
| Spring Boot | 2.2.6.RELEASE | Anwendungsframework |
| Spring JMS | 5.2.5.RELEASE | JMS-Abstraktionsschicht |
| Apache ActiveMQ | 5.15.12 | Message Broker |
| Jackson | 2.10.3 | JSON Serialisierung/Deserialisierung |
| Java Swing | — | GUI-Framework |

---

## 12. ActiveMQ Broker

| Eigenschaft | Wert |
|---|---|
| Host | `192.168.111.6` |
| JMS-Port | `61616` |
| Web-Interface | http://192.168.111.6:8161 (admin/admin) |
| Queues | http://192.168.111.6:8161/admin/queues.jsp |
| Topics | http://192.168.111.6:8161/admin/topics.jsp |

---

**Gruppe 6 – FHNW FS26, Software Architecture**
