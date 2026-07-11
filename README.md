# 🦅 VooApp — Simulador de Voo com o Corpo

Um simulador de voo de mundo aberto para **Android** onde você é o pássaro:
a câmera frontal rastreia seus movimentos em tempo real e transforma seus
braços em asas. Abra os braços, bata para ganhar altitude e incline para virar.

Feito com **Kotlin + CameraX + ML Kit Pose Detection** (o modelo de pose vem
embutido no app, então funciona **offline**, sem baixar nada em tempo de
execução) e um motor de jogo 2.5D desenhado em `Canvas`.

## ✨ Destaques

- 🧊 **Visual 3D** — prédios como caixas extrudadas com faces e topo sombreados,
  chão em grade com perspectiva, argolas como anéis 3D (com furo para
  atravessar), pássaro volumétrico que rola ao inclinar e névoa no horizonte.
- 🌅 **Ciclo dia/noite** contínuo — o céu transiciona (amanhecer → dia → pôr
  do sol → noite), o sol vira lua, estrelas aparecem e a cidade acende as luzes.
- 🏙️ **Mundo vivo** — cidade com profundidade, rodovia com carros (com faróis
  à noite), árvores, nuvens e pássaros voando ao fundo.
- 🎈 **Obstáculos** — balões que fazem você cair; desvie deles!
- ⚡ **Power-ups** — **Turbo** (mais velocidade + invencibilidade temporária) e
  **Escudo** (absorve uma batida).
- 🔊 **Sons sintetizados** — "ding" na argola, som de asas, impacto na queda e
  jingle ao pegar power-up (gerados em runtime, sem arquivos, funciona offline).
- 💥 **Efeitos** — brilho, halo e "+pontos" nas argolas; penas e tremor de tela
  ao cair; linhas de vento e rastro ao planar.
- 📳 **Vibração** ao pontuar e ao cair.
- 🏆 **Recorde salvo** entre partidas (SharedPreferences).
- 📈 **Dificuldade progressiva** — velocidade e obstáculos aumentam com a distância.

## 🐦 Escolha seu pássaro

Na tela inicial você **toca** para escolher entre quatro pássaros, cada um com
cores e desempenho próprios:

| Pássaro    | Característica                    |
| ---------- | -------------------------------- |
| 🦅 Águia   | Mais força para subir            |
| 🪶 Falcão  | Mais rápido                      |
| 🦜 Papagaio| Equilibrado                      |
| 🐦 Arara   | Ágil                             |

## 🎮 Como jogar

O controle é todo pelo corpo, na frente da câmera:

| Movimento seu                              | No jogo                          |
| ------------------------------------------ | -------------------------------- |
| Braços **para cima**                       | Sobe                             |
| Braços **para baixo**                      | Desce                            |
| **Bater** os braços para cima rapidamente  | Impulso de altitude (flap)       |
| **Inclinar** as asas (um braço mais baixo) | Vira para aquele lado            |
| Braços **bem esticados** para os lados     | Plana mais rápido                |

- Levante os braços para **decolar** (sair da tela inicial).
- Atravesse as **argolas douradas** para pontuar e fazer combo.
- Se bater no chão com força, você cai — levante os braços para voltar a voar.
- Fique visível para a câmera; o esqueleto no canto mostra o que está sendo
  rastreado (o ponto no canto inferior fica verde quando há rastreio).

## 🏗️ Como compilar

Requer o **Android SDK** (via Android Studio) — o wrapper do Gradle já está
incluído no repositório.

### Android Studio (recomendado)
1. Abra a pasta do projeto no Android Studio.
2. Deixe o Gradle sincronizar (ele baixa o Android SDK/dependências).
3. Conecte um celular Android (com câmera) e clique em **Run ▶**.

### Linha de comando
```bash
# aponte para o seu Android SDK
echo "sdk.dir=/caminho/para/Android/sdk" > local.properties

./gradlew assembleDebug        # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # instala no aparelho conectado
```

> Use um **aparelho físico**. Emuladores geralmente não têm câmera frontal com
> uma pessoa de corpo inteiro para a detecção de pose funcionar bem.

## 📁 Estrutura

```
app/src/main/java/com/vooapp/birdflight/
├── MainActivity.kt              # permissão + CameraX + fiação geral
├── camera/
│   ├── PoseAnalyzer.kt          # ML Kit em cada frame -> PoseFrame -> FlightInput
│   └── PoseFrame.kt             # articulações normalizadas
├── input/
│   ├── FlightInput.kt           # comandos de voo (lift, flap, roll, spread)
│   └── PoseInterpreter.kt       # traduz pose -> comandos (com suavização)
├── audio/
│   └── SoundFx.kt              # efeitos sonoros sintetizados (AudioTrack)
└── game/
    ├── BirdType.kt              # tipos de pássaro (cores + desempenho)
    ├── GameEngine.kt            # física, argolas, obstáculos, power-ups, colisão
    ├── GameRenderer.kt          # cena 3D: cidade, rodovia, dia/noite, efeitos
    ├── GameView.kt              # SurfaceView + loop + toque + som + vibração + recorde
    └── PoseOverlayView.kt       # esqueleto sobre a prévia da câmera
```

## ⚙️ Ajustes finos

- Se o **giro parecer invertido** no seu aparelho, troque `INVERT_ROLL` em
  `input/PoseInterpreter.kt`.
- Sensibilidade de voo (gravidade, força do flap, etc.): constantes no fim de
  `game/GameEngine.kt`.
- Sensibilidade dos controles de pose: constantes em `input/PoseInterpreter.kt`.

## 📋 Requisitos

- `minSdk` 24 (Android 7.0+), `targetSdk` 35.
- Permissão de **câmera** (pedida na primeira execução).
