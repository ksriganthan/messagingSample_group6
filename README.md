# Problemfall 3 – Auftrags-Dispositionssystem via Messaging (Gruppe 6)

## 1. Projektübersicht

Dieses Projekt implementiert ein verteiltes **Auftrags-Dispositionssystem** auf Basis von
**Apache ActiveMQ** (Message Broker) und **Spring Boot JMS** (Java Message Service).

Die Architektur bildet einen realistischen Geschäftsprozess ab: Eine zentrale **Disposition** erstellt
Aufträge (Reparaturen und Wartungen) und verteilt diese über einen Message Broker an **Clients (Arbeiter)**.
Die Arbeiter können Aufträge einsehen, nach Region und Auftragstyp filtern und einzelne Aufträge
zur Zuweisung anfragen. Die Disposition prüft, ob der Auftrag noch verfügbar ist, und bestätigt
oder lehnt die Anfrage ab.

### Module

| Modul | Rolle | Beschreibung |
|---|---|---|
| **MessagePublisher** | Disposition | Erzeugt fortlaufend neue Aufträge und veröffentlicht sie. Empfängt Zuweisungsanfragen und prüft, ob ein Auftrag noch frei ist. |
| **MessageConsumer** | Client / Arbeiter | Empfängt Aufträge vom Broker, filtert sie clientseitig nach Region und Auftragstyp, und fragt automatisch eine Zuweisung an. |

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
┌─────────────────────────────────────────────────────────────────────────┐
│                     ActiveMQ Broker (192.168.111.6:61616)               │
│                                                                         │
│  ┌─────────────────────────────┐    ┌────────────────────────────────┐  │
│  │ Topic: group6.dispo.jobs.new│    │ Topic: group6.dispo.jobs.      │  │
│  │                             │    │        assignments             │  │
│  └──────────▲──────────────────┘    └───────────────┬────────────────┘  │
│             │                                       │                   │
│  ┌──────────┴──────────────────────────────────┐    │                   │
│  │ Queue: group6.dispo.jobs.requestAssignment  │    │                   │
│  └──────────────────────────▲──────────────────┘    │                   │
│                             │                       │                   │
└─────────────────────────────┼───────────────────────┼───────────────────┘
                              │                       │
       ┌──────────────────────┼───────────────────────┼──────────┐
       │                      │                       │          │
       │  SCHRITT 1           │  SCHRITT 3            ▼          │
       │  Publisher erzeugt   │  Publisher sendet                 │
       │  Auftrag und sendet  │  Antwort (JA/NEIN)              │
       │  auf Topic           │  auf Topic                       │
       │         │            │                                  │
       │         │     ┌──────┴────────┐                         │
       │         │     │  Publisher    │                          │
       │         │     │ (Disposition) │                          │
       │         │     └──────▲────────┘                         │
       │         │            │                                  │
       │         │     SCHRITT 2                                 │
       │         │     Consumer sendet                           │
       │         │     Anfrage auf Queue                         │
       │         │            │                                  │
       │         ▼            │           SCHRITT 4              │
       │  ┌───────────────────┴───┐       Consumer empfängt      │
       │  │     Consumer          │       Bestätigung            │
       │  │  (Client/Arbeiter)    │◄─────────────────────        │
       │  └───────────────────────┘                              │
       └─────────────────────────────────────────────────────────┘

Schritt 1: Disposition veröffentlicht alle 2 Sekunden einen neuen Auftrag auf dem Topic.
Schritt 2: Client empfängt den Auftrag, prüft Region + Typ, und sendet eine Zuweisungsanfrage.
Schritt 3: Disposition prüft ob der Auftrag noch frei ist und sendet Antwort (zugewiesen/abgelehnt).
Schritt 4: Client empfängt die Antwort und zeigt das Ergebnis an.
```

---

## 3. Channels (Topics & Queues)

### 3.1 Namenskonvention

Gemäss den Vorgaben des Dozenten müssen alle eigenen Topics und Queues mit dem **Gruppennamen
gefolgt von einem Punkt** beginnen. Da wir Gruppe 6 sind, verwenden wir das Prefix `group6.`:

```
group6.dispo.jobs.new
group6.dispo.jobs.requestAssignment
group6.dispo.jobs.assignments
```

Damit sind unsere Channels klar von anderen Gruppen und dem Fremdsystem des Dozenten getrennt.
Das ermöglicht einen parallelen Betrieb auf demselben Broker ohne Konflikte.

### 3.2 Channel-Übersicht

| Channel-Name | Typ | Richtung | Nachrichtentyp | Beschreibung |
|---|---|---|---|---|
| `group6.dispo.jobs.new` | **Topic** | Disposition → Clients | `JobMessage` | Neue Aufträge werden hier veröffentlicht (alle 2 Sek.) |
| `group6.dispo.jobs.requestAssignment` | **Queue** | Client → Disposition | `JobRequestMessage` | Clients senden Zuweisungsanfragen an diese Queue |
| `group6.dispo.jobs.assignments` | **Topic** | Disposition → Clients | `JobAssignmentMessage` | Zuweisungsantworten werden hier veröffentlicht |

### 3.3 Warum Topic für Aufträge und Antworten, aber Queue für Anfragen?

- **Aufträge (Topic)**: Alle Clients sollen jeden neuen Auftrag sehen, um entscheiden zu können,
  ob er für sie relevant ist. Ein Topic stellt sicher, dass die Nachricht an alle Subscriber
  gleichzeitig zugestellt wird.

- **Zuweisungsanfragen (Queue)**: Eine Anfrage soll **genau einmal** verarbeitet werden.
  Wenn mehrere Instanzen der Disposition liefen, würde eine Queue sicherstellen, dass jede
  Anfrage nur von einer Instanz bearbeitet wird (Load Balancing).

- **Zuweisungsantworten (Topic)**: Die Antwort wird über ein Topic verteilt, damit alle Clients
  sie empfangen. Jeder Client filtert dann selbst, ob die Antwort an ihn gerichtet ist
  (anhand der `clientId`).

---

## 4. Nachrichtentypen

Alle Nachrichten werden als **JSON** über den Broker übertragen. Die Umwandlung zwischen
Java-Objekten und JSON erfolgt automatisch über den `MappingJackson2MessageConverter`.

### 4.1 JobMessage – Neuer Auftrag

Wird von der Disposition auf dem Topic `group6.dispo.jobs.new` veröffentlicht.

```json
{
  "jobId": "JOB-0001",
  "description": "repair Auftrag #1",
  "region": "basel",
  "jobType": "repair"
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | Eindeutige Auftrags-ID (z.B. `JOB-0001`) |
| `description` | String | Beschreibung des Auftrags |
| `region` | String | Region des Auftrags (`basel`, `zürich`, `bern`) |
| `jobType` | String | Art des Auftrags (`repair` = Reparatur, `maintenance` = Wartung) |

### 4.2 JobRequestMessage – Zuweisungsanfrage

Wird vom Client an die Queue `group6.dispo.jobs.requestAssignment` gesendet.

```json
{
  "jobId": "JOB-0001",
  "clientId": "group6"
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des gewünschten Auftrags |
| `clientId` | String | ID des anfragenden Clients (z.B. `group6`) |

### 4.3 JobAssignmentMessage – Zuweisungsantwort

Wird von der Disposition auf dem Topic `group6.dispo.jobs.assignments` veröffentlicht.

```json
{
  "jobId": "JOB-0001",
  "clientId": "group6",
  "assigned": true
}
```

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des angefragten Auftrags |
| `clientId` | String | ID des anfragenden Clients |
| `assigned` | boolean | `true` = Auftrag zugewiesen, `false` = Auftrag abgelehnt (bereits vergeben) |

---

## 5. Disposition (MessagePublisher)

### 5.1 Aufgabe

Der Publisher simuliert die zentrale Disposition. Er hat zwei Verantwortlichkeiten:

1. **Aufträge erzeugen und veröffentlichen**: Alle 2 Sekunden wird ein neuer Auftrag erstellt
   und auf dem Topic `group6.dispo.jobs.new` veröffentlicht. Die Aufträge rotieren durch die
   Regionen (Basel → Zürich → Bern) und Typen (Repair → Maintenance).

2. **Zuweisungsanfragen verarbeiten**: Der Publisher hört auf die Queue
   `group6.dispo.jobs.requestAssignment`. Wenn ein Client einen Auftrag anfragt, prüft die
   Disposition, ob der Auftrag bereits vergeben ist.

### 5.2 Duplikatsprüfung

Um zu verhindern, dass derselbe Auftrag an mehrere Clients vergeben wird, führt die Disposition
eine `ConcurrentHashMap` (Thread-sicher), die alle vergebenen Aufträge speichert:

```java
Map<String, String> assignedJobs = new ConcurrentHashMap<>();  // JobId → ClientId
```

Bei einer neuen Anfrage wird `putIfAbsent()` verwendet:
- **Rückgabe `null`** → Der Auftrag war noch nicht vergeben → Zuweisung akzeptiert ✅
- **Rückgabe `"group3"`** → Der Auftrag ist bereits an `group3` vergeben → Zuweisung abgelehnt ❌

Diese Methode ist **atomar** (Thread-sicher), sodass auch bei gleichzeitigen Anfragen
korrekt geprüft wird.

### 5.3 JMS-Konfiguration (Publisher)

| Bean | Typ | Zweck |
|---|---|---|
| `topicJmsTemplate` | JmsTemplate | Sendet Nachrichten an **Topics** (Aufträge + Antworten) |
| `queueFactory` | ListenerContainerFactory | Empfängt Nachrichten von **Queues** (Zuweisungsanfragen) |
| `jacksonJmsMessageConverter` | MessageConverter | Wandelt Java-Objekte ↔ JSON um |

---

## 6. Client (MessageConsumer)

### 6.1 Aufgabe

Der Consumer ist der Client / Arbeiter. Er hat drei Verantwortlichkeiten:

1. **Aufträge empfangen**: Hört auf das Topic `group6.dispo.jobs.new` und empfängt alle
   neuen Aufträge.

2. **Aufträge filtern**: Bevor ein Auftrag verarbeitet wird, prüft der Client zwei
   konfigurierbare Filter:
   - **Region-Filter**: Nur Aufträge der konfigurierten Region werden angenommen.
   - **JobType-Filter**: Nur Aufträge des konfigurierten Typs werden angenommen.
   Sind die Filter leer, werden alle Aufträge akzeptiert.

3. **Zuweisung anfragen und Antwort empfangen**: Für jeden akzeptierten Auftrag sendet der Client
   automatisch eine Zuweisungsanfrage an die Queue. Anschliessend empfängt er die Antwort
   vom Topic `group6.dispo.jobs.assignments` und zeigt an, ob der Auftrag zugewiesen oder
   abgelehnt wurde.

### 6.2 Clientseitige Filterung

Die Filterung findet bewusst auf der **Client-Seite** statt und nicht auf dem Broker.
Der Broker liefert über das Topic alle Aufträge an alle Subscriber. Jeder Client entscheidet
dann selbst, ob der Auftrag für ihn relevant ist. Dieser Ansatz ist einfach umzusetzen und
entspricht der vereinfachten Lösung gemäss Aufgabenstellung. In einem Produktionssystem würde
man alternativ einen **Content-Based Router** auf Broker-Ebene einsetzen, der Aufträge
automatisch an regionsspezifische Channels verteilt.

```java
// Region-Filter: Auftrag ignorieren, wenn er nicht zur konfigurierten Region passt
if (filterRegion != null && !filterRegion.isEmpty()
        && !filterRegion.equalsIgnoreCase(job.getRegion())) {
    return;
}

// JobType-Filter: Auftrag ignorieren, wenn er nicht zum konfigurierten Typ passt
if (filterJobType != null && !filterJobType.isEmpty()
        && !filterJobType.equalsIgnoreCase(job.getJobType())) {
    return;
}
```

### 6.3 Zuweisungsantworten filtern

Da Zuweisungsantworten über ein **Topic** verteilt werden, empfangen alle Clients alle Antworten.
Jeder Client prüft daher, ob die Antwort an ihn gerichtet ist:

```java
if (!clientId.equals(assignment.getClientId())) {
    return;  // Antwort ist für einen anderen Client
}
```

### 6.4 JMS-Konfiguration (Consumer)

| Bean | Typ | Zweck |
|---|---|---|
| `queueJmsTemplate` | JmsTemplate | Sendet Nachrichten an **Queues** (Zuweisungsanfragen) |
| `topicFactory` | ListenerContainerFactory | Empfängt Nachrichten von **Topics** (Aufträge + Antworten) |
| `jacksonJmsMessageConverter` | MessageConverter | Wandelt Java-Objekte ↔ JSON um |

Die JMS-Konfiguration ist **spiegelverkehrt** zum Publisher: Der Publisher sendet auf Topics
und empfängt von Queues, der Consumer sendet auf Queues und empfängt von Topics.

---

## 7. Konfiguration

Alle konfigurierbaren Werte befinden sich in den `application.properties`-Dateien.
So lässt sich das Verhalten ändern, ohne den Quellcode anzupassen.

### 7.1 Publisher (MessagePublisher)

```properties
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=admin
spring.activemq.password=admin

channel.topic.newJobs=group6.dispo.jobs.new
channel.queue.requestAssignment=group6.dispo.jobs.requestAssignment
channel.topic.assignments=group6.dispo.jobs.assignments
```

### 7.2 Consumer (MessageConsumer)

```properties
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=admin
spring.activemq.password=admin

client.id=group6
client.region=                    # leer = alle Regionen
client.jobType=                   # leer = alle Typen

channel.topic.newJobs=group6.dispo.jobs.new
channel.queue.requestAssignment=group6.dispo.jobs.requestAssignment
channel.topic.assignments=group6.dispo.jobs.assignments
```

### 7.3 Filteroptionen

#### Region-Filter

| Wert | Verhalten |
|---|---|
| `client.region=` | Empfängt Aufträge **aller** Regionen |
| `client.region=basel` | Nur Aufträge der Region Basel |
| `client.region=zürich` | Nur Aufträge der Region Zürich |
| `client.region=bern` | Nur Aufträge der Region Bern |

#### JobType-Filter

| Wert | Verhalten |
|---|---|
| `client.jobType=` | Empfängt **alle** Auftragstypen |
| `client.jobType=repair` | Nur Reparatur-Aufträge |
| `client.jobType=maintenance` | Nur Wartungs-Aufträge |

Beide Filter lassen sich kombinieren:
```properties
client.region=basel
client.jobType=repair
# → Nur Reparatur-Aufträge aus der Region Basel
```

---

## 8. Starten der Anwendung

### 8.1 Voraussetzungen

- Java 11+ (getestet mit OpenJDK 25)
- Maven
- ActiveMQ Broker erreichbar unter `192.168.111.6:61616`

### 8.2 Reihenfolge

```
1. ActiveMQ Broker muss laufen (192.168.111.6:61616)
2. MessagePublisher starten (simuliert die Disposition)
3. MessageConsumer starten (Client / Arbeiter)
```

Der Publisher sollte **zuerst** gestartet werden, da der Consumer sonst die ersten Aufträge
verpasst (Topics liefern nur Nachrichten an aktive Subscriber).

### 8.3 Aus IntelliJ IDEA

1. **MessagePublisher**: `MessageApplication.java` → Rechtsklick → Run
2. **MessageConsumer**: `MessageConsumerApplication.java` → Rechtsklick → Run

### 8.4 Aus der Kommandozeile (Maven)

```bash
# Terminal 1: Publisher (Disposition) starten
cd MessagePublisher
mvn spring-boot:run

# Terminal 2: Consumer (Client) starten
cd MessageConsumer
mvn spring-boot:run
```

---

## 9. GUI

Beide Module verfügen über eine einfache **Swing-Oberfläche** zur Laufzeitüberwachung.

### Publisher-Fenster
- Titel: `"Disposition - Job Publisher"`
- Zeigt veröffentlichte Aufträge und Zuweisungsentscheidungen im Log

### Consumer-Fenster
- Titel: `"Auftrags-Client [group6] - Region: alle | Typ: alle"`
- Zeigt empfangene Aufträge, Zuweisungsanfragen und Bestätigungen im Log
- Titel passt sich dynamisch an die aktiven Filter an

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
│       │   ├── SimpleUi.java                  # Swing GUI (Log-Anzeige)
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
│       │   ├── Receiver.java                   # Aufträge empfangen + filtern
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
