# Requisitos — App de Assistência Visual para Meta Quest 3

## Introdução

Aplicativo de realidade mista para Meta Quest 3/3S que auxilia pessoas com
deficiência visual, usando as câmeras frontais do headset (Passthrough Camera API)
para executar tarefas de visão computacional — detecção de objetos, leitura de
texto (placas/letreiros) e detecção de pessoas — e comunicar os resultados ao
usuário por meio de **feedback de áudio** (síntese de voz e áudio espacial).

O app é construído com **Meta Spatial SDK** (Kotlin/Android nativo), roda modelos
de ML **on-device** para baixa latência e privacidade, e opcionalmente usa um
serviço **cloud** (LLM multimodal) para descrições de cena mais ricas.

### Contexto de projeto (pesquisa acadêmica)
- Autor: estudante de Ciência da Computação
- Objetivo: pesquisa e protótipo funcional de óculos inteligente assistivo
- Plataforma-alvo: Meta Quest 3 / 3S (Horizon OS v74+)

## Glossário
- **PCA**: Passthrough Camera API (acesso às câmeras RGB frontais do Quest)
- **TTS**: Text-to-Speech (síntese de voz)
- **OCR**: Optical Character Recognition (reconhecimento de texto)
- **On-device**: processamento local no headset, sem rede

## Requisitos

### Requisito 1 — Acesso à câmera de passthrough
**User Story:** Como usuário com deficiência visual, quero que o app acesse a câmera
frontal do headset, para que ele possa "ver" o ambiente por mim.

#### Acceptance Criteria
1. QUANDO o app é iniciado pela primeira vez ENTÃO o sistema DEVE solicitar a
   permissão de câmera (`android.permission.CAMERA` ou `horizonos.permission.HEADSET_CAMERA`).
2. SE a permissão for negada ENTÃO o sistema DEVE informar o usuário por áudio e
   oferecer nova tentativa.
3. QUANDO a permissão é concedida ENTÃO o sistema DEVE abrir o stream da câmera
   frontal a uma resolução testada (1280x960) e formato YUV420.
4. O sistema DEVE escolher a resolução dinamicamente a partir da lista suportada,
   NÃO assumindo a maior resolução disponível (compatibilidade futura).
5. SE o passthrough não estiver habilitado ENTÃO o sistema NÃO DEVE tentar abrir a
   câmera e DEVE registrar erro claro.

### Requisito 2 — Detecção de objetos
**User Story:** Como usuário, quero que o app identifique objetos à minha frente,
para que eu saiba o que há no ambiente.

#### Acceptance Criteria
1. QUANDO um frame da câmera está disponível ENTÃO o sistema DEVE executar um
   modelo de detecção de objetos on-device sobre o frame.
2. O sistema DEVE manter taxa de processamento que preserve conforto (meta: >= 10 FPS
   de inferência, sem travar o render do headset).
3. QUANDO objetos são detectados com confiança acima de um limiar configurável
   ENTÃO o sistema DEVE anunciar por áudio o rótulo e a posição relativa
   (ex.: "cadeira à sua esquerda").
4. O sistema DEVE evitar repetir o mesmo anúncio em intervalos curtos (debounce).

### Requisito 3 — Leitura de texto (OCR)
**User Story:** Como usuário, quero que o app leia placas, letreiros e textos,
para que eu possa navegar e me informar.

#### Acceptance Criteria
1. QUANDO o usuário aciona o modo leitura (gesto/controle/voz) ENTÃO o sistema DEVE
   capturar um frame e executar OCR on-device.
2. QUANDO texto é reconhecido ENTÃO o sistema DEVE lê-lo em voz alta via TTS.
3. SE nenhum texto for encontrado ENTÃO o sistema DEVE informar "nenhum texto detectado".
4. O sistema DEVE suportar português e inglês no OCR e no TTS.

### Requisito 4 — Detecção de pessoas
**User Story:** Como usuário, quero saber quando há pessoas próximas, para
interação social e segurança.

#### Acceptance Criteria
1. QUANDO uma pessoa (rosto/corpo) é detectada no frame ENTÃO o sistema DEVE
   anunciar presença e posição relativa.
2. O sistema NÃO DEVE identificar ou reconhecer indivíduos específicos (apenas
   detecção de presença), respeitando privacidade e LGPD.
3. O sistema NÃO DEVE persistir imagens de rostos.

### Requisito 5 — Feedback de áudio
**User Story:** Como usuário com deficiência visual, quero receber toda informação
por áudio claro, pois não posso depender da tela.

#### Acceptance Criteria
1. O sistema DEVE usar TTS para todos os anúncios. O TTS DEVE ser **offline e
   embarcado no app** (o Quest não possui motor TTS do sistema), via sherpa-onnx.
2. O sistema DEVE usar áudio espacial/pan estéreo para indicar direção quando aplicável.
3. O sistema DEVE permitir controlar a verbosidade (mínima/normal/detalhada).
4. QUANDO múltiplos anúncios ocorrem ENTÃO o sistema DEVE enfileirá-los sem
   sobreposição confusa (fila com prioridade).

### Requisito 6 — Descrição de cena via cloud (opcional)
**User Story:** Como usuário, quero descrições ricas do ambiente sob demanda,
usando IA avançada quando houver internet.

#### Acceptance Criteria
1. QUANDO o usuário solicita "descrever cena" E há conectividade ENTÃO o sistema
   DEVE enviar um frame a um serviço LLM multimodal e ler a descrição retornada.
2. SE não houver conectividade ENTÃO o sistema DEVE recorrer à descrição on-device
   (objetos detectados) e informar o modo offline.
3. O sistema DEVE tornar o envio à cloud explícito e desativável (privacidade).
4. O sistema NÃO DEVE enviar imagens à cloud sem consentimento configurado.

### Requisito 7 — Privacidade e conformidade
**User Story:** Como pesquisador, preciso que o app respeite políticas de dados
da Meta e a LGPD.

#### Acceptance Criteria
1. O sistema DEVE tratar imagens da câmera como Device User Data (política Meta).
2. O sistema NÃO DEVE armazenar frames em disco por padrão.
3. O sistema DEVE documentar claramente quais dados saem do dispositivo (cloud).
4. O sistema DEVE funcionar 100% offline no modo padrão.

### Requisito 8 — Build e deploy no ambiente Aurora/distrobox
**User Story:** Como desenvolvedor no Aurora Linux (imutável), quero buildar e
implantar o app no Quest a partir do meu ambiente configurado.

#### Acceptance Criteria
1. O projeto DEVE compilar via `./gradlew :app:assembleDebug` dentro da distrobox `quest-dev`.
2. O projeto DEVE usar o toolchain validado: JDK 17, AGP 8.11.1, Kotlin 2.1.0, NDK 27.
3. O projeto DEVE implantar via `adb install`/`./gradlew :app:installDebug` com o Quest
   em modo desenvolvedor.
4. O projeto NÃO DEVE exigir alterações no sistema imutável do host.
