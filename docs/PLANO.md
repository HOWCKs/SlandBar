# Plano — SlandBar: barra flutuante de atalhos e widgets

> Como vamos fazer do jeito **certo**, com base na referência (Notch Touch) e nas regras reais da Google Play.

## 1. O que estamos construindo

Um aplicativo Android que desenha uma **barra flutuante** (pílula) por cima de qualquer app e, ao toque, expande um **painel de atalhos e widgets**. O usuário personaliza tudo: tamanho, posição, opacidade, tema, gestos, atalhos e widgets.

Referência: **Notch Touch** (com.chaos.notchtouch) — 100 mil+ downloads, nota 4,7. O diferencial do SlandBar: **grátis por um bom prazo** (núcleo gratuito para sempre) e mais transparência.

## 2. Decisão técnica fundamental: Android nativo (Kotlin + Compose)

| Alternativa | Por quê NÃO |
|---|---|
| React Native / Flutter | Janelas flutuantes sobre outros apps exigem controle fino do `WindowManager`; dá para contornar, mas a complexidade e o risco de bugs são maiores |
| **Android nativo (Kotlin + Jetpack Compose)** ✅ | APIs nativas de overlay, acessibilidade, mídia e sistema; menor latência; é o que apps do tipo usam (chat bubbles, Assistive Touch, Notch Touch) |
| iOS | **Não é possível**: a Apple não permite janelas sobrepostas a outros apps. Overlay flutuante é exclusividade Android |

**Stack escolhida**: Kotlin 2.0, Jetpack Compose (Material 3), Coroutines + StateFlow, DataStore (preferências), Gson (serialização de atalhos), Coil (capa do álbum), Google Play Billing.

## 3. Arquitetura do app

```
┌────────────────────────────────────────────────────┐
│ MainActivity (configurações, Compose)              │
│   └─ SettingsScreen: permissões, aparência, gestos,│
│      atalhos, widgets, Premium (Play Billing)      │
├────────────────────────────────────────────────────┤
│ SlandOverlayService (ForegroundService specialUse) │
│   ├─ Janela 1: pílula flutuante (arrastável)       │
│   │    gestos: toque, duplo, longo, deslizar       │
│   ├─ Janela 2: painel expandido (scrim + card)     │
│   ├─ MediaController → sessões de mídia do sistema │
│   ├─ TimerController (cronômetro/temporizador)     │
│   └─ ActionExecutor → ações reais de sistema       │
├────────────────────────────────────────────────────┤
│ SlandAccessibilityService                          │
│   ├─ Captura de tela (takeScreenshot, API 30+)     │
│   ├─ Menu de energia / sombra de notificações      │
│   └─ Recolhe a barra com o teclado aberto          │
├────────────────────────────────────────────────────┤
│ SlandWidgetProvider (AppWidget da tela inicial)    │
├────────────────────────────────────────────────────┤
│ SlandPrefs (DataStore) — toda a configuração       │
│ BillingManager (Play Billing, pagamento único)     │
└────────────────────────────────────────────────────┘
```

### Permissões e por que cada uma existe (texto para a Play Store)

| Permissão | Uso real | Nota de política |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Desenhar a barra sobre outros apps | Categoria "sobreposição" — uso legítimo (atalhos flutuantes) |
| `FOREGROUND_SERVICE` + `specialUse` | Manter a barra viva em segundo plano | Declarada no manifest com subtipo `floating_overlay_shortcuts_widgets` |
| `POST_NOTIFICATIONS` | Notificação discreta do serviço | Pedida com explicação |
| `CAMERA` | **Somente** para o flash (lanterna) | Pedida sob demanda, com explicação |
| `WRITE_SETTINGS` | Controle de brilho pelo painel | Opcional; encaminha para a tela do sistema |
| `ACCESS_NOTIFICATION_POLICY` | Toggle "Não perturbe" | Opcional |
| `VIBRATE` | Perfis de vibração dos gestos | Normal |
| Accessibility Service | Captura de tela, menu de energia, teclado | **Disclosure obrigatório**: declaração clara de uso, sem coleta de dados |
| `RECEIVE_BOOT_COMPLETED` | Reativar a barra ao ligar o aparelho (opcional) | Normal |

**Regra de ouro**: nenhuma permissão é pedida "de graça"; cada uma tem card explicativo na tela inicial e é solicitada quando o usuário for usar o recurso.

## 4. Modelo de negócio: grátis de verdade + Premium honesto

O usuário pediu "gratuito por um bom prazo". Em vez de trial com data de validade (que gera review negativo e desinstalação), o modelo certo é:

**Núcleo gratuito para sempre** (sem anúncios no uso normal):
- Barra flutuante completa, gestos, snap, auto-ocultar
- Controles rápidos (lanterna, DND, rotação, som)
- Até **6 atalhos** personalizados
- Widgets: relógio, cronômetro/temporizador, brilho
- Temas: sistema, claro, escuro + cores dinâmicas (Material You)

**SlandBar Premium — pagamento único** (R$ ~9,90 ou equivalente):
- Atalhos ilimitados
- Widget de música (capa do álbum + controles)
- Controle de volume no painel
- Cores de destaque personalizadas + temas extras
- Perfis de vibração avançados (mecânico / profundo)

Por que pagamento único e não assinatura: é um app de ferramenta — usuário paga uma vez, avalia 5 estrelas, recomenda. Assinatura em app de utilidade pequena gera churn e reviews negativos.

**Planos de renda futuros** (v2, opcionais): versão "Pro" com Tasker + gestos de borda; parceria de widgets; doações. Anúncios só se o custo de servidor exigir — e nunca dentro do painel flutuante.

## 5. Políticas do Google Play (checklist de publicação)

1. **Declaração de acessibilidade**: formulário "Accessibility Service declaration" no Play Console, com o texto de disclosure (`accessibility_description`) e o uso exclusivo do serviço.
2. **Permissão de sobreposição**: o app entra na categoria de uso de sobreposição — a Play pode exigir vídeo demonstrando o uso (a barra flutuante é o uso).
3. **Política de dados**: o app **não coleta nada** (100% local) → formulário "Data Safety" com zero compartilhamento. Pronto em `docs/PRIVACIDADE.md`.
4. **FGS specialUse**: declaração no manifest + descrição no formulário do Play Console.
5. **Icones, screenshots e descrição**: gerar com o build real (sem IA genérica no store listing — screenshots reais de aparelho).
6. **Conta de desenvolvedor**: US$ 25 única, dados verificados.

## 6. Fases do projeto

| Fase | Entrega | Status |
|---|---|---|
| **Fase 0 — Fundação** | Projeto Android completo (overlay, gestos, painel, ações, permissões, freemium, widget, acessibilidade) + preview do frontend + docs | ✅ neste repositório |
| **Fase 1 — Beta privado** | Compilar no Android Studio, instalar em 2–3 aparelhos reais, caçar bugs de OEM (Xiaomi/realme exigem "Auto-start" e permissões extras), afinar UI | próximo passo |
| **Fase 2 — Play Store (v1.0)** | Conta dev, Data Safety, declaração de acessibilidade, listing, release em produção (rollout 10% → 100%) | — |
| **Fase 3 — v1.1** | Gestos na borda/notch (estilo Notch Touch), Tasker, widget de clima/bateria, mais temas | — |
| **Fase 4 — v1.2** | Analytics opcional (opt-in), crash reporting, melhorias de desempenho | — |

## 7. O que já está pronto (Fase 0, neste repo)

- ✅ Projeto Android completo (`android/`): 17 arquivos Kotlin, manifesto com todas as permissões e serviços, recursos (ícone adaptativo, strings pt-BR/en, widget)
- ✅ Barra flutuante real: arrastar, snap nas bordas, auto-ocultar, 4 gestos mapeáveis, vibração
- ✅ Painel expandido: relógio, controles rápidos, atalhos, música, cronômetro/temporizador, brilho, volume
- ✅ Ações reais de sistema (lanterna, captura de tela, DND, rotação, som, menu de energia, abrir apps)
- ✅ Acessibilidade com disclosure, widget de tela inicial, boot receiver
- ✅ Freemium via Play Billing (Premium automático em build debug)
- ✅ Preview do frontend (`preview/`) para iterar o design no navegador
- ✅ Política de privacidade (`docs/PRIVACIDADE.md`)

## 8. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Overlay bloqueado por OEMs (MIUI, ColorOS, HarmonyOS) | Tela de ajuda por fabricante; atalho direto para as permissões; testes na Fase 1 |
| Google rejeitar acessibilidade | Disclosure claro + uso mínimo + vídeo demonstrativo |
| Bateria em segundo plano | Serviço leve (sem polling pesado; mídia via callbacks), notificação `IMPORTANCE_MIN`, auto-ocultar |
| Android 14+ restringe FGS | Tipo `specialUse` declarado + justificativa no console |
| Concorrência (Notch Touch etc.) | Diferencial: grátis com recursos essenciais, sem assinatura, transparência total |

## 9. Próximos passos imediatos (com você)

1. Abrir `android/` no Android Studio e gerar o APK de debug;
2. Instalar no seu aparelho e testar a Fase 1;
3. Iterar o visual no `preview/` (tema, cores, tamanhos) e espelhar as mudanças no Compose;
4. Definir preço do Premium e nome da conta de desenvolvedor;
5. Publicar na Play Store (beta fechado → aberto → produção).
