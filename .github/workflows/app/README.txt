╔══════════════════════════════════════════╗
║        ROB RUN ESP — BUILD GUIDE         ║
╚══════════════════════════════════════════╝

REQUISITOS
──────────
• Android Studio Hedgehog (2023.1) ou mais novo
  https://developer.android.com/studio
• JDK 17 (já vem com o Android Studio)
• Celular com Android 8+ (minSdk 26)
• Modo desenvolvedor + "Fontes desconhecidas" ativados

COMO BUILDAR
────────────
1. Abra o Android Studio
2. File → Open → selecione a pasta "robrun-esp-apk"
3. Aguarde o Gradle sync (baixa dependências automaticamente)
4. Conecte o celular via USB (ou use emulador)
5. Run → "Run app" (ou Shift+F10)
   → Android Studio instala o APK direto no celular

PARA GERAR O APK MANUAL (sem USB)
──────────────────────────────────
Build → Build Bundle(s)/APK(s) → Build APK(s)
O APK gerado fica em:
  app/build/outputs/apk/debug/app-debug.apk

Transfira para o celular e instale (permita fontes desconhecidas).

PRIMEIRO USO NO CELULAR
────────────────────────
1. Abra "RobRun ESP"
2. Toque "1. Permitir overlay" → ative nas configurações
3. Volte ao app → "2. Iniciar ESP" → aceite a captura de tela
4. Abra o Rob Run e jogue até aparecer a tela de roleta
5. Volte ao ESP → "3. Definir zona de captura"
   → uma tela transparente abre
   → arraste o dedo sobre a área onde aparece a cor/número do resultado
   → solte para confirmar
6. Pronto — o overlay aparece em cima do jogo e lê sozinho

COMO FUNCIONA
─────────────
• MediaProjection captura frames da tela a cada 1.5s
• MLKit OCR (on-device, sem internet) lê o texto da zona selecionada
• O engine detecta palavras-chave de cor (Vermelho, Preto, Verde, etc.)
  e números de 0-36
• Novo resultado → adiciona ao histórico → recalcula probabilidades
• O painel flutuante mostra tudo em tempo real
• Arraste o painel para mover; toque no header para minimizar

ADICIONAR MAIS CORES
─────────────────────
Edite ProbabilityEngine.kt → COLOR_KEYWORDS
Adicione as palavras que aparecem na tela do Rob Run:
  "suapalavra" to "NomeDaCor"

PERMISSÕES USADAS
──────────────────
• SYSTEM_ALERT_WINDOW  — overlay flutuante
• FOREGROUND_SERVICE   — serviço em background
• MEDIA_PROJECTION     — captura de tela (pedida em tempo real)
Nenhuma permissão de internet, câmera ou armazenamento.
