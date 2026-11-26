# Should I Stay or Should I Go? · Smart Office Planner

Proof of concept del team **Depeche Code** (hackathon Randstad Digital).

Il sistema consiglia **in quali giorni andare in ufficio** e **in quale area sedersi** per massimizzare le relazioni di lavoro, e prepara con l'AI gli **inviti ai colleghi chiave**.

## Avvio rapido

Requisiti: **JDK 17+** e Maven (va bene IntelliJ IDEA, che li include).

```bash
cd backend
mvn spring-boot:run
```

Poi apri **http://localhost:8080**. Il frontend Angular è già compilato dentro `backend/src/main/resources/static`.

In IntelliJ: *Open* sulla cartella `backend`, attendi l'import Maven, poi Run su `OfficePlannerApplication`.

### Attivare l'AI (Google Gemini, default)

Imposta la chiave come variabile d'ambiente prima di avviare. **Non scriverla mai nei file: il repo è pubblico.**
- IntelliJ: *Run → Edit Configurations → OfficePlannerApplication → Environment variables* → `GEMINI_API_KEY=...`
- Windows PowerShell: `$env:GEMINI_API_KEY="..."` poi `mvn spring-boot:run`

Senza chiave l'app funziona lo stesso: testi a template e chat interpretata a regole, e la UI lo segnala.

Per cambiare provider basta la configurazione (endpoint compatibile OpenAI):

| Variabile | Default (Gemini) | GitHub Models |
|---|---|---|
| `LLM_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/openai` | `https://models.github.ai/inference` |
| `LLM_API_KEY` | valore di `GEMINI_API_KEY` | token GitHub con permesso Models: read |
| `LLM_MODEL` | `gemini-2.5-flash` | `openai/gpt-4.1-mini` |
| `LLM_REASONING_EFFORT` | `none` | vuoto |

### Chat: aggiornare gli impegni in linguaggio naturale

Nella landing c'è un campo chat: "oggi Chiara ha un workshop obbligatorio", "venerdì Marco Bianchi ha una demo al cliente", "annulla l'impegno di Chiara di lunedì".
L'LLM trasforma la frase in un comando JSON validato (persona, giorno, REQUIRED/OPTIONAL/FREE, nota); se l'AI non risponde subentra un parser a regole.
Il dato viene aggiornato e il **consiglio della settimana** della persona interessata viene rigenerato. Endpoint: `POST /api/chat {userId, message}`.

### Sviluppo frontend

```bash
cd frontend
npm install
npm start               # http://localhost:4200, proxy verso :8080
npm run build:backend   # ricompila e copia la build dentro Spring
```

## Come funziona

**Il motore decide, l'LLM spiega.** Lo scoring è deterministico (stabile, testabile, spiegabile); l'LLM trasforma il risultato in un consiglio in linguaggio naturale e scrive i messaggi di invito.

| Driver della sfida | Regola | Punti |
|---|---|---|
| Tipologia di attività | giorno con impegno obbligatorio in presenza | +100 |
| Tipologia di attività | evento facoltativo (jolly) | +15 |
| Densità del core team | ogni collega del team già prenotato | +10 |
| Affinità | ogni collega con la stessa skill nell'area | +3 |
| Mentorship | junior con un senior della stessa skill in area (o viceversa) | +15 |
| Capienza | area piena | giorno escluso |

I giorni **GO** sono: quelli già prenotati, quelli obbligatori, poi i migliori per punteggio fino ai giorni minimi del profilo. I full remote vanno solo nei giorni obbligatori. I pesi sono in `application.properties`.

**Inviti proattivi**: per ogni giorno GO il motore propone i colleghi del team non ancora prenotati (e, per i junior, i senior della stessa skill). L'AI scrive il messaggio; con "Invia invito" la demo simula l'accettazione e ricalcola tutto.

## Architettura

```
backend/   Spring Boot 3 · Java 17
  domain/       record del modello dati (User, Area, Booking, ...)   puro Java
  engine/       RecommendationEngine + ScoringWeights                 puro Java, testato
  ai/           LlmClient (porta) · OpenAiCompatibleLlmClient · AiNarrator (prompt + fallback)
  repository/   OfficeRepository (porta) · JsonOfficeRepository (mock JSON in memoria)
  service/      PlannerService (casi d'uso)
  api/          PlannerController (REST /api)
frontend/  Angular 22 · standalone components · signals
```

### Dal PoC al prodotto enterprise

| Livello | PoC | Prodotto |
|---|---|---|
| Dati | `JsonOfficeRepository` | `JpaOfficeRepository` su PostgreSQL, stessa interfaccia |
| Agenda | mock JSON | integrazione Outlook / Google Calendar |
| Inviti | simulati | Microsoft Teams / email |
| LLM | GitHub Models via config | gateway aziendale, Azure OpenAI o modello on-prem |
| Identità | selezione utente | SSO aziendale (Entra ID) |
| Motore | pesi statici | pesi per sede/progetto, feedback sulle presenze reali |
| Deploy | `mvn spring-boot:run` | container, CI/CD, scalabilità orizzontale (servizio stateless) |

## API

| Metodo | Path | Descrizione |
|---|---|---|
| GET | `/api/week`, `/api/users`, `/api/areas`, `/api/bookings` | dati di base |
| GET | `/api/users/{id}/recommendation` | verdetto GO/STAY per giorno |
| GET | `/api/users/{id}/summary` | consiglio della settimana (AI) |
| POST | `/api/bookings` | prenota `{userId, date, areaId}` |
| POST | `/api/invites/draft` | bozza invito (AI) `{fromUserId, date, toUserIds}` |
| POST | `/api/invites/accept` | simula l'accettazione degli inviti |
| POST | `/api/chat` | comando in linguaggio naturale, restituisce il consiglio aggiornato |
| GET | `/api/today` | giorno corrente della demo |
| POST | `/api/reset` | ripristina i dati demo |

## Script demo (3 minuti)

1. **Chiara Neri**, junior FE del team Payments: GO lunedì (onboarding) e martedì (c'è Marco, il suo senior). Giovedì il team c'è ma l'area è piena: STAY.
2. Lunedì → **Invita 4**: l'AI scrive il messaggio, *Invia invito*, i colleghi prenotano e la matrice presenze si aggiorna.
3. **Federica Gatti**, full remote: GO solo mercoledì per la visita del management.
4. **Marco Bianchi**, senior: il motore gli segnala i junior da affiancare.
5. *Reset demo* prima di ogni prova.
