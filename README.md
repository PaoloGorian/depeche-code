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

### Attivare l'AI con Google Gemini (consigliato)

Crea il file `backend/config/application.properties` (è in `.gitignore`, la chiave non finisce nel repo). Spring lo legge da solo se avvii da `backend/`:

```properties
llm.base-url=https://generativelanguage.googleapis.com/v1beta/openai
llm.api-key=LA_TUA_CHIAVE_GEMINI
llm.model=gemini-3.8-flash
llm.fallback-models=gemini-3.6-flash,gemini-3-flash-preview,gemini-flash-lite-latest,gemini-3.5-flash-lite
```

I modelli di fallback servono quando Gemini risponde "high demand" (503): il client passa al successivo e mette in pausa per 60 secondi quello sovraccarico. In IntelliJ imposta la *Working directory* della run configuration sulla cartella `backend`.

### Attivare l'AI (GitHub Models)

1. Su GitHub: *Settings → Developer settings → Fine-grained tokens*, crea un token con permesso **Models: read**.
2. Esporta il token prima di avviare:
   - macOS/Linux: `export GITHUB_TOKEN=github_pat_...`
   - Windows PowerShell: `$env:GITHUB_TOKEN="github_pat_..."`
   - IntelliJ: *Run configuration → Environment variables* → `GITHUB_TOKEN=github_pat_...`

Senza token l'app funziona lo stesso: i testi arrivano da template e la UI lo segnala.

Per cambiare provider basta la configurazione, senza toccare il codice:

| Variabile | Default | Esempi alternativi |
|---|---|---|
| `LLM_BASE_URL` | `https://models.github.ai/inference` | `https://api.openai.com/v1`, `http://localhost:11434/v1` (Ollama) |
| `LLM_API_KEY` | valore di `GITHUB_TOKEN` | chiave OpenAI / Azure |
| `LLM_MODEL` | `openai/gpt-4.1-mini` | `gpt-4o-mini`, `llama3.1` |
| `LLM_FALLBACK_MODELS` | vuoto | modelli di riserva separati da virgola |

### Pianifica con l'AI (chat)

Il pannello è una chat: il primo messaggio è il consiglio per l'utente loggato. L'utente può scrivere i suoi impegni ("giovedì ho un cliente in sede", "martedì sono dal medico"): l'LLM li estrae in JSON (`REQUIRED`, `OPTIONAL`, `REMOTE`, `FREE`), il motore ricalcola il piano e l'AI risponde sul piano aggiornato. Sotto il messaggio compaiono le card dei giorni con Prenota / Annulla / Invita.

API: `POST /api/users/{id}/chat` con `{"messages":[...]}` (vuoto = consiglio iniziale), `DELETE /api/bookings/{userId}/{date}` per annullare una prenotazione.

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
| POST | `/api/reset` | ripristina i dati demo |

## Script demo (3 minuti)

1. **Chiara Neri**, junior FE del team Payments: GO lunedì (onboarding) e martedì (c'è Marco, il suo senior). Giovedì il team c'è ma l'area è piena: STAY.
2. Lunedì → **Invita 4**: l'AI scrive il messaggio, *Invia invito*, i colleghi prenotano e la matrice presenze si aggiorna.
3. **Federica Gatti**, full remote: GO solo mercoledì per la visita del management.
4. **Marco Bianchi**, senior: il motore gli segnala i junior da affiancare.
5. *Reset demo* prima di ogni prova.
