# AI assistant setup (PC side)

The TV can't run a language model. The model runs on your PC under [Ollama](https://ollama.com), and Glass TV streams replies from `http://<pc>:11434/api/chat` over your home network. If the PC is off, the assistant panel says so and everything else keeps working.

## 1. Install Ollama and pull a model

Install from https://ollama.com/download (Windows, macOS, Linux), then:

```sh
ollama pull llama3.2:3b      # ~2 GB, fast on almost any PC
# or
ollama pull gemma3:4b        # ~3.3 GB, a bit smarter, wants ~6 GB free RAM/VRAM
```

Check that it answers: `ollama run llama3.2:3b "say hi"`.

## 2. Let the TV reach it: `OLLAMA_HOST=0.0.0.0`

By default Ollama only listens on `127.0.0.1`, so the TV can't connect.

**Windows:** quit Ollama from the tray, then in PowerShell:
```powershell
setx OLLAMA_HOST 0.0.0.0
```
Start Ollama again from the Start menu. `setx` only applies to newly started programs.

**macOS (app):**
```sh
launchctl setenv OLLAMA_HOST 0.0.0.0
```
Quit and reopen Ollama. This resets on reboot; to make it permanent, add it to a login item or use `brew services` with the env set.

**Linux (systemd):**
```sh
sudo systemctl edit ollama
# add:
# [Service]
# Environment="OLLAMA_HOST=0.0.0.0"
sudo systemctl daemon-reload && sudo systemctl restart ollama
```

Verify from another device on the Wi-Fi: open `http://<pc-ip>:11434` in a browser. It should say *"Ollama is running"*.

## 3. Open port 11434 in the PC firewall (private network only)

**Windows (PowerShell as Administrator):**
```powershell
New-NetFirewallRule -DisplayName "Ollama (LAN)" -Direction Inbound -Protocol TCP -LocalPort 11434 -Action Allow -Profile Private
```
Make sure your home Wi-Fi is set to **Private** in Windows network settings. Undo: `Remove-NetFirewallRule -DisplayName "Ollama (LAN)"`.

**macOS:** System Settings → Network → Firewall → Options → allow incoming connections for Ollama.

**Linux (ufw):** `sudo ufw allow from 192.168.0.0/16 to any port 11434 proto tcp`

Don't port-forward 11434 on your router. Ollama has no authentication.

## 4. Give the PC a stable address

Pick one:
- **DHCP reservation (best):** in your router's admin page, reserve the PC's current IP for its MAC address.
- **Hostname:** many routers resolve `your-pc-name.local` or `your-pc-name.lan`. Try `ping your-pc-name.local` from another device.

## 5. Point Glass TV at it

TV → Glass TV → **Settings → Assistant**:
- **PC address:** `192.168.1.50` (Glass TV adds `:11434` if you leave the port off), or `my-pc.local`
- **Model:** `llama3.2:3b` (must match what you pulled)
- Press **Test connection**.

## What the error messages mean

| Message on TV | Fix |
|---|---|
| The assistant isn't set up yet | Enter the PC address in Settings → Assistant. |
| Can't find your PC on the network | The hostname doesn't resolve. Use the IP address instead. |
| Your PC isn't answering | PC off/asleep, Ollama not running, `OLLAMA_HOST` not set, or the firewall is blocking 11434. |
| The model "…" isn't installed | Run `ollama pull <model>` on the PC. |
| Your PC took too long | The model is loading (first request after idle can take 10–30 s on CPU). Ask again. |

Tip: to stop the PC sleeping during movie night, set *Sleep: Never* while plugged in, or use Wake-on-LAN.

## Privacy

Prompts go only from the TV to your PC on your LAN. Glass TV calls no cloud AI service.
