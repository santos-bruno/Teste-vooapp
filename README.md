# 🦅 VooApp — Simulador de Voo com o Corpo

Um simulador de voo de mundo aberto para **Android** onde você é o pássaro:
a câmera frontal rastreia seus movimentos em tempo real e transforma seus
braços em asas. Abra os braços, bata para ganhar altitude e incline para virar.

Feito com **Kotlin + CameraX + ML Kit Pose Detection** (o modelo de pose vem
embutido no app, então funciona **offline**, sem baixar nada em tempo de
execução) e um motor de jogo 2.5D desenhado em `Canvas`.

## ✨ Destaques

- 🌅 **Ciclo dia/noite** contínuo — o céu transiciona (amanhecer → dia → pôr
  do sol → noite), o sol vira lua, estrelas aparecem e a cidade acende as luzes.
- 🏙️ **Mundo vivo** — cidade no horizonte, rodovia com carros em movimento (com
  faróis à noite), árvores, nuvens e pássaros voando ao fundo.
- 💥 **Efeitos** — explosão de brilho, halo e "+pontos" ao cruzar as argolas;
  penas voando e tremor de tela ao cair; linhas de vento e rastro ao planar.
- 📳 **Vibração** ao pontuar e ao cair.
- 📈 **Dificuldade progressiva** — a velocidade aumenta com a distância.

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
└── game/
    ├── BirdType.kt              # tipos de pássaro (cores + desempenho)
    ├── GameEngine.kt            # física do voo, argolas, pontuação, colisão
    ├── GameRenderer.kt          # cena pseudo-3D: cidade, rodovia, árvores, sol, pássaro
    ├── GameView.kt              # SurfaceView + loop + toque para escolher o pássaro
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
