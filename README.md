# TelecomandoElio

App Android locale per comandare Fully Kiosk sulla stessa Wi-Fi.

## Funzionamento

- Un solo pulsante principale.
- **BLOCCA**: invia `enableLockedMode`, attende 650 ms, poi invia `startScreensaver`.
- **SBLOCCA**: invia soltanto `disableLockedMode`.
- **AutoMode ON**: dopo ogni SBLOCCA parte un timer (default 5 minuti); allo scadere esegue BLOCCA + screensaver.
- **AutoMode OFF**: nessun blocco automatico.
- IP predefinito: `192.168.1.10`
- Password predefinita: `2222`
- Porta Fully Remote Admin: `2323`

Le impostazioni IP/password/minuti sono modificabili nell'app.
