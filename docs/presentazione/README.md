# Presentazione alla giuria · Depeche Code

**Slide (Canva):** https://canva.link/ymgrwri18hk0o4e
I file esportati vanno in questa cartella: `pitch-depeche-code.pdf` e `pitch-depeche-code.pptx`.

Durata: circa 5 minuti · 8 slide · stile minimal, palette Randstad.
La giuria chiede quattro elementi, ognuno ha la sua slide: **01 Prototipo · 02 Architettura · 03 Roadmap e costi · 04 Business model e ROI**.

## Chi parla

| Slide | Titolo | Chi | Tempo |
|---|---|---|---|
| 1 | Should I stay or should I go? | 🎓 Junior | 20" |
| 2 | L'ufficio a caso | 🎓 Junior | 35" |
| 3 | 01 · GO o STAY (prototipo + demo) | 🎓 Junior | 50" |
| 4 | Il motore decide, l'AI spiega | 💼 Senior | 40" |
| 5 | 02 · Architettura | 💼 Senior | 40" |
| 6 | 03 · Roadmap e costi | 💼 Senior | 40" |
| 7 | 04 · Business model e ROI | 💼 Senior | 45" |
| 8 | Ogni giorno in ufficio ha un motivo | 🎓 Junior + 💼 Senior | 20" |

La junior racconta il problema e il prodotto (l'esperienza di chi lo usa), il senior racconta la tecnica e i numeri (perché funziona e perché conviene). La chiusura la fate insieme.

---

## 🎓 Junior

**Slide 1 · Should I stay or should I go?**
"Ciao a tutti, siamo i Depeche Code: senior di Randstad Digital e studenti TTF. La nostra sfida porta il titolo di una canzone dei Clash, *Should I stay or should I go?*. Noi abbiamo costruito l'app che risponde a questa domanda, ogni giorno."

**Slide 2 · L'ufficio a caso**
"Partiamo da una scena che conosciamo tutti. Vai in ufficio, prenoti la prima scrivania libera e passi la giornata in videocall con colleghi rimasti a casa. Il tuo team non c'è. Il senior che poteva sbloccarti è venuto ieri. Hai fatto un'ora di viaggio per lavorare come da casa. Il lavoro ibrido ci ha dato flessibilità, ma l'ufficio lo usiamo a caso. E da junior lo senti ancora di più: è in ufficio che impari, se ci trovi le persone giuste."

**Slide 3 · 01 · GO o STAY**
"Noi lo trasformiamo in una sola parola: GO oppure STAY. Appena entri vedi la tua settimana, e ogni giorno ti dice se ti conviene venire o restare a casa. Sotto c'è la mappa live dell'ufficio: aree colorate, scrivanie, e sopra i colleghi che ci saranno. Vedi subito chi c'è e dove. La scrivania non la cerchi tu: te la propone il sistema, vicino al tuo team e ai senior della tua stessa skill. E se manca qualcuno di importante, con un click l'AI scrive l'invito e la mappa si aggiorna. Ve lo facciamo vedere."
*(20 secondi di demo: login, click su un giorno, la mappa cambia, si apre "Pianifica con l'AI")*
"Non è un disegno, è un prototipo funzionante. E adesso il perché funziona ve lo spiega [nome del senior]."

---

## 💼 Senior

**Slide 4 · Il motore decide, l'AI spiega**
"La scelta che ci distingue è questa: il motore decide, l'AI spiega. Il punteggio segue regole chiare. Un impegno obbligatorio in presenza vale cento punti, ogni collega del team presente dieci, una coppia junior-senior quindici, e se l'area è piena il giorno è escluso. Così il risultato è stabile, testabile e spiegabile: nessuna allucinazione decide dove ti siedi. L'intelligenza artificiale fa quello che sa fare meglio, cioè trasformare i numeri in un consiglio umano e scrivere i messaggi ai colleghi."

**Slide 5 · 02 · Architettura**
"È costruito per crescere. Frontend Angular, backend Spring Boot su Azure, database PostgreSQL e Azure OpenAI, con i dati in Europa. Il provider AI si cambia da configurazione, senza toccare il codice. Ci si collega con l'account aziendale tramite Entra ID, e calendari e inviti passano da Outlook e Teams, quindi nessuna app nuova da imparare. Sulla privacy siamo rigorosi: dei calendari salviamo solo se un giorno è obbligatorio o libero, mai i contenuti degli appuntamenti."

**Slide 6 · 03 · Roadmap e costi**
"Per arrivare al prodotto servono sei mesi. Nei primi due facciamo un pilota in una sede con account e calendari reali. Nei due successivi lo testiamo con duecento persone, aggiungendo gli inviti su Teams e le planimetrie vere. Negli ultimi due andiamo in produzione su più sedi. Stimiamo circa centocinquantamila euro di sviluppo, con un team di quattro o cinque persone, e circa mille euro al mese di cloud e AI ogni mille utenti. L'AI costa poco perché la usiamo per spiegare, non per calcolare."

**Slide 7 · 04 · Business model e ROI**
"Facciamo due conti su mille dipendenti in ibrido. Se ognuno risparmia mezz'ora a settimana tra chat, mail e 'chi viene domani?', sono circa ventitremila ore l'anno. Con queste ipotesi l'investimento rientra in meno di quattro mesi, e non stiamo ancora contando gli uffici usati meglio e i junior che crescono più in fretta accanto ai senior. Il modello è doppio: prima lo adottiamo in Randstad, poi lo offriamo ai clienti come servizio SaaS, tra i tre e i cinque euro per utente al mese."

---

## 🎓 + 💼 Chiusura

**Slide 8 · Ogni giorno in ufficio ha un motivo**
💼 Senior: "Il punto non è riportare le persone in ufficio, è dare a ogni giorno in ufficio un motivo."
🎓 Junior: "Meno giorni a caso, più relazioni. *Should I stay or should I go?* Da oggi ve lo dice Depeche Code."
Insieme: "Grazie!"

---

## Note per la giuria (domande probabili)

- **Da dove vengono i numeri del ROI?** Ipotesi: 30 minuti a settimana a persona, 46 settimane lavorative, 1.000 dipendenti, costo medio di circa 30 €/ora. Sono stime da validare nel pilota.
- **Perché non lasciare decidere tutto all'AI?** Perché una regola deterministica è spiegabile e verificabile; l'LLM serve a comunicare, non a decidere.
- **Privacy?** Dal calendario si legge solo se un giorno è obbligatorio o libero; nessun contenuto degli appuntamenti viene salvato. Dati in region UE, SSO aziendale.
