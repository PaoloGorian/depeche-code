# Presentazione alla giuria · Depeche Code

**Slide (Canva):** https://canva.link/ymgrwri18hk0o4e
I file esportati vanno in questa cartella: `pitch-depeche-code.pdf` e `pitch-depeche-code.pptx`.

Durata: **5 minuti** · 8 slide · stile minimal, palette Randstad.
La giuria chiede quattro elementi, ognuno ha la sua slide: **01 Prototipo · 02 Architettura · 03 Roadmap e costi · 04 Business model e ROI**.

## Il filo logico

Una sola idea guida tutto il pitch: **ogni giorno in ufficio deve avere un motivo**.

1. **Il problema**: l'ufficio ibrido si usa a caso.
2. **La risposta**: per ogni giorno una parola, GO o STAY.
3. **La prova (demo)**: vedo la mia settimana, confermo i GO prenotando, parlo con l'AI e il piano si adatta.
4. **Perché ci si può fidare**: il motore decide con regole chiare, l'AI capisce e spiega.
5. **Perché cresce, quanto costa, quanto rende.**
6. **Chiusura**: di nuovo il motivo.

La junior racconta l'esperienza di chi usa l'app, il senior racconta la tecnica e i numeri. La chiusura la fate insieme.

## Chi parla e quanto

| Slide | Titolo | Chi | Tempo |
|---|---|---|---|
| 1 | Should I stay or should I go? | 🎓 Junior | 15" |
| 2 | L'ufficio a caso | 🎓 Junior | 30" |
| 3 | 01 · GO o STAY (prototipo + demo live) | 🎓 Junior | 1'30" |
| 4 | Il motore decide, l'AI capisce e spiega | 💼 Senior | 35" |
| 5 | 02 · Architettura | 💼 Senior | 30" |
| 6 | 03 · Roadmap e costi | 💼 Senior | 30" |
| 7 | 04 · Business model e ROI | 💼 Senior | 35" |
| 8 | Ogni giorno in ufficio ha un motivo | 🎓 + 💼 | 15" |
| | **Totale** | | **≈ 5'00"** |

## Cosa aggiornare su Canva

| Slide | Da cambiare rispetto alla versione attuale |
|---|---|
| 3 | Screenshot nuovo della home: tab GO/STAY con "✓ Prenotato / Da prenotare", contatore "giorni prenotati" nell'header, riquadro con **Prenota**. Tre parole chiave: **Vedi · Conferma · Chiedi all'AI**. |
| 4 | Aggiungere una seconda riga: "L'AI capisce i tuoi impegni in linguaggio naturale e riscrive il consiglio". Esempio in evidenza: *"Venerdì ho una demo al cliente"* → venerdì diventa GO. |
| 5 | Nel blocco AI: "provider da configurazione (Gemini, Azure OpenAI, GitHub Models)" e "fallback a regole: la demo non si ferma mai". |
| 6, 7, 8 | Invariate. |

---

## 🎓 Junior

**Slide 1 · Should I stay or should I go?** *(15")*
"Ciao a tutti, siamo i Depeche Code: senior di Randstad Digital e studenti TTF. La nostra sfida porta il titolo di una canzone dei Clash, e noi abbiamo costruito l'app che risponde a quella domanda, ogni giorno."

**Slide 2 · L'ufficio a caso** *(30")*
"Una scena che conosciamo tutti: prenoti la prima scrivania libera e passi la giornata in videocall con chi è rimasto a casa. Il tuo team non c'è, il senior che poteva sbloccarti è venuto ieri. Un'ora di viaggio per lavorare come da casa. Il lavoro ibrido ci ha dato flessibilità, ma l'ufficio lo usiamo a caso. E da junior lo senti di più: è in ufficio che impari, se ci trovi le persone giuste."

**Slide 3 · 01 · GO o STAY** *(1'30", demo live)*
"Noi lo trasformiamo in una sola parola: GO oppure STAY. E un GO vale solo quando è confermato da una prenotazione. Ve lo facciamo vedere."

*Demo, passo per passo (vedi la checklist più sotto):*

1. **Login come Chiara**, junior frontend. *"Questa è la mia settimana: lunedì e martedì GO, gli altri giorni STAY. In alto vedo che ho prenotato 0 giorni su 2 e che ho 2 GO da confermare."*
2. **Clic su lunedì → Prenota.** *"La mappa mi dice chi c'è e dove: sono nell'area frontend, vicino a una senior della mia skill. Prenoto, e il contatore sale a 1 su 2."*
3. **Apro "Pianifica con l'AI".** *"Qui c'è il consiglio della settimana, scritto dall'AI partendo dai dati."*
4. **Scrivo in chat: "Venerdì ho una demo al cliente".** *"Le parlo come a un collega. L'AI capisce chi, quando e che tipo di impegno, aggiorna il mio calendario e riscrive il consiglio: adesso venerdì è un GO."*
5. **Chiudo il pannello → venerdì → Prenota.** *"Confermo, e il contatore diventa verde: 2 su 2."*

"Non è un disegno, è un prototipo funzionante. Perché funziona ve lo spiega [nome del senior]."

---

## 💼 Senior

**Slide 4 · Il motore decide, l'AI capisce e spiega** *(35")*
"La scelta che ci distingue: il motore decide, l'AI capisce e spiega. Il punteggio segue regole chiare: un impegno obbligatorio vale cento punti, ogni collega del team presente dieci, una coppia junior-senior quindici, un'area piena esclude il giorno. Il risultato è stabile e verificabile: nessuna allucinazione decide dove ti siedi. L'AI fa due cose in cui è forte: capisce le frasi in linguaggio naturale, che noi validiamo prima di toccare i dati, e trasforma i numeri in un consiglio umano. Se l'AI non risponde, subentrano le regole e l'app continua a funzionare."

**Slide 5 · 02 · Architettura** *(30")*
"È costruito per crescere. Frontend Angular, backend Spring Boot, motore in Java puro coperto da test. Dati e AI sono dietro interfacce: oggi un file di esempio e Gemini, domani PostgreSQL e Azure OpenAI con i dati in Europa, senza toccare il motore. Accesso con l'account aziendale tramite Entra ID, calendari e inviti da Outlook e Teams. Sulla privacy: dei calendari salviamo solo se un giorno è obbligatorio o libero, mai i contenuti."

**Slide 6 · 03 · Roadmap e costi** *(30")*
"Al prodotto si arriva in sei mesi: due di pilota in una sede con account e calendari reali, due di test con duecento persone con inviti su Teams e planimetrie vere, due di rilascio su più sedi. Circa centocinquantamila euro di sviluppo con quattro o cinque persone, e circa mille euro al mese di cloud e AI ogni mille utenti. L'AI costa poco perché la usiamo per capire e spiegare, non per calcolare."

**Slide 7 · 04 · Business model e ROI** *(35")*
"Due conti su mille dipendenti in ibrido: se ognuno risparmia mezz'ora a settimana tra chat, mail e 'chi viene domani?', sono circa ventitremila ore l'anno. Con queste ipotesi l'investimento rientra in meno di quattro mesi, senza contare uffici usati meglio e junior che crescono più in fretta accanto ai senior. Il modello è doppio: prima lo adottiamo in Randstad, poi lo offriamo ai clienti come SaaS, tra i tre e i cinque euro per utente al mese."

---

## 🎓 + 💼 Chiusura

**Slide 8 · Ogni giorno in ufficio ha un motivo** *(15")*
💼 Senior: "Il punto non è riportare le persone in ufficio, è dare a ogni giorno in ufficio un motivo."
🎓 Junior: "Meno giorni a caso, più relazioni. Da oggi la risposta ve la dà Depeche Code."
Insieme: "Grazie!"

---

## Checklist demo (prima di salire sul palco)

- [ ] Backend avviato da IntelliJ con `GEMINI_API_KEY` (o GitHub Models) e frontend aggiornato (`git pull`, Stop + Run).
- [ ] http://localhost:8080/api/ai/ping risponde con `"source":"llm"`. Se dice `template`, la demo regge lo stesso: dite "qui vedete il fallback a regole".
- [ ] **Riavviare il backend subito prima del pitch**: le prenotazioni della demo sono in memoria e il riavvio riporta i dati di partenza.
- [ ] Login come **Chiara Neri** (chiara.neri@depechecode.example).
- [ ] Frase della chat già pronta: **"Venerdì ho una demo al cliente"**. Piano B se l'AI è lenta: cliccare uno dei suggerimenti sotto la chat.
- [ ] Browser a schermo intero, zoom 100%, notifiche disattivate.

## Note per la giuria (domande probabili)

- **Da dove vengono i numeri del ROI?** Ipotesi: 30 minuti a settimana a persona, 46 settimane lavorative, 1.000 dipendenti, costo medio di circa 30 €/ora. Sono stime da validare nel pilota.
- **Perché non lasciare decidere tutto all'AI?** Perché una regola deterministica è spiegabile e verificabile; l'LLM serve a capire e a comunicare, non a decidere.
- **E se l'AI capisce male?** Il comando viene validato (persona e giorno devono esistere) prima di toccare i dati; la risposta in chat dice cosa è stato aggiornato e si corregge con una frase ("annulla il mio impegno di venerdì").
- **Privacy?** Dal calendario si legge solo se un giorno è obbligatorio o libero; nessun contenuto degli appuntamenti viene salvato. Dati in region UE, SSO aziendale.
