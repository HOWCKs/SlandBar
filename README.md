# SlandBar

**Barra flutuante de atalhos e widgets para Android** — uma "Dynamic Island / Notch Touch" gratuita e personalizável, feita com Kotlin + Jetpack Compose.

A SlandBar é uma pílula flutuante que fica por cima de **qualquer aplicativo** (overlay de sistema). Toque para expandir um painel com controles rápidos, seus atalhos favoritos, widget de música, cronômetro/temporizador, brilho e volume — tudo configurável.

```
┌────────────────────────────────────────────┐
│  🕘 09:41 │ 🔋87% │ 🔦 │ 🎵 │ ⠿  ← pílula flutuante
└────────────────────────────────────────────┘
        ▼ toque (ou deslize para cima)
┌────────────────────────────────────────────┐
│  SlandBar · 9 de agosto de 2026        ▾ ✕ │
│  09:41:22                                  │
│  CONTROLES RÁPIDOS                         │
│  (🔦) (🌙) (🔄) (🔔)                      │
│  ATALHOS                                   │
│  (💬) (🎵) (📷) (🔦)                      │
│  MÚSICA [PREMIUM]                          │
│  ⏮  ▶  ⏭                                 │
│  TEMPORIZADOR                              │
│  00:00  [▶ Iniciar] [↺ Zerar]              │
│  BRILHO ————————●————                      │
└────────────────────────────────────────────┘
```

## 📲 Baixar o APK (compilado pelo GitHub)

O GitHub Actions compila o APK na nuvem e publica em **Releases** — baixe direto no celular:

1. Abra a aba **Releases** do repositório (ou o link fixo abaixo);
2. Toque em **app-debug.apk** para baixar;
3. Instale (pode pedir "permitir instalação de apps desconhecidos" — normal para APK fora da Play Store).

Links:
- Releases: <https://github.com/HOWCKs/SlandBar/releases>
- APK mais recente: <https://github.com/HOWCKs/SlandBar/releases/latest/download/app-debug.apk>

> Para gerar um novo APK: crie uma tag `v*` (ex.: `v0.9.1`) ou use o botão
> **"Run workflow"** na aba **Actions → Build APK** — sem precisar de computador.

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `android/` | **O app real** — projeto Android nativo (Kotlin + Jetpack Compose), pronto para abrir no Android Studio e gerar o APK |
| `preview/` | **Preview interativo do frontend** — réplica fiel da UI rodando no navegador (para iterar o design antes de compilar) |
| `docs/` | Plano do produto e política de privacidade |

## O app real (`android/`)

App Android nativo com:

- **Barra flutuante** (overlay `SYSTEM_ALERT_WINDOW`) — arrastável, com snap nas bordas, auto-ocultar, escala e opacidade configuráveis
- **Gestos**: toque simples, toque duplo, toque longo e deslizar — cada um mapeável para qualquer ação
- **Painel expandido** com widgets: relógio, controles rápidos (lanterna, DND, rotação, modo de som), atalhos, música (controle de sessões de mídia do sistema), cronômetro/temporizador, brilho e volume
- **Ações reais**: abrir apps, lanterna (CameraManager), captura de tela (AccessibilityService), menu de energia, sombras de notificação, DND, rotação, modo de som
- **Acessibilidade**: serviço usado apenas para captura de tela, menu de energia e recolher a barra com o teclado — sem coleta de dados (disclosure exigido pelo Google Play)
- **Widget na tela inicial** (AppWidget) com 4 atalhos rápidos
- **Personalização total**: tamanho, opacidade, tema claro/escuro/sistema, cores dinâmicas (Material You), cor de destaque, perfis de vibração, gestos, atalhos e widgets
- **Freemium real**: núcleo gratuito para sempre + Premium (pagamento único via Google Play Billing): atalhos ilimitados, widget de música, volume, cores e vibrações avançadas

### Como compilar

1. Instale o [Android Studio](https://developer.android.com/studio) (Ladybug ou mais novo).
2. Abra a pasta `android/` como projeto ("Open").
3. Aguarde o Gradle sincronizar (baixa as dependências automaticamente).
4. Run ▶ num aparelho ou emulador (Android 8.0+ / API 26+).
5. Primeiro uso: ative **"Permitir sobreposição"** na tela de boas-vindas e ligue a barra flutuante.

Também funciona por linha de comando com o SDK instalado:

```bash
cd android
./gradlew assembleDebug        # gera app-debug.apk
./gradlew assembleRelease      # build de produção (assinado por você)
```

> O APK **não é compilado dentro deste workspace** porque a rede do sandbox bloqueia os servidores do Google/Maven — mas o projeto está completo e verificado para compilar na sua máquina.

## O preview (`preview/`)

Um protótipo interativo 1:1 da UI do overlay + tela de configurações, rodando no navegador. Útil para validar o design, o fluxo de gestos e a personalização antes de fechar o visual no app nativo. **Não é o app** — é o laboratório de frontend.

Para rodar localmente:

```bash
cd preview && python3 -m http.server 8080
```

## Documentação

- [`docs/PLANO.md`](docs/PLANO.md) — plano completo do produto e da engenharia
- [`docs/PRIVACIDADE.md`](docs/PRIVACIDADE.md) — política de privacidade (pronta para a Play Store)

## Roadmap resumido

- ✅ **v0.9 (este repo)**: projeto Android completo + preview do frontend
- ☐ **v1.0**: build de teste (beta privado), refinamento de UI, testes em aparelhos reais
- ☐ **v1.1**: gestos na borda/notch (estilo Notch Touch), Tasker, mais widgets
- ☐ **v1.2**: publicação na Google Play (freemium)

Licença: uso livre para estudo; publicação comercial exige acordo com o autor.
