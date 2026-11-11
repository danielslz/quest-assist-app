# Privacidade e dados que saem do dispositivo

Documento do item **8.3** (Requisito 7.3). Descreve o que o app captura, onde o
dado fica, o que pode sair do headset e em quais condições.

> Escopo: este documento descreve o comportamento **do código deste repositório**
> (verificado por leitura do código). Onde algo depende de comportamento de
> terceiros (ML Kit) ou não foi medido no device, isso está indicado em
> "O que NÃO foi verificado".

## 1. Resumo

| Modo | Rede necessária? | Imagem sai do headset? |
|---|---|---|
| **Padrão** (nuvem desligada): objetos, OCR, voz | Não, em uso normal (ver exceção da seção 4) | **Não** |
| **Nuvem ativada** (opt-in) + ação "Descrever" | Sim, para o endpoint configurado | **Sim**, 1 frame JPEG por pedido de descrição |

Princípios aplicados no código:
- Frames da câmera **nunca são gravados em disco**.
- Detecção de pessoas é **apenas de presença** (sem identificação nem
  reconhecimento facial; sem persistir rostos).
- O envio à nuvem é **opt-in**, desligado por padrão, e só ocorre quando o
  usuário aciona "Descrever".

## 2. O que o app captura e onde fica

| Dado | Origem | Onde fica | Persistido? |
|---|---|---|---|
| Frame da câmera (YUV/NV21) | Passthrough Camera API | Memória: o último frame fica retido para OCR/descrição sob demanda, até ser substituído | **Não** |
| Bitmap do preview e caixas de detecção | Painel de debug | Memória (estado do painel) | **Não** |
| Texto reconhecido (OCR) e descrição | OCR / LLM / fallback | Memória; é falado e guardado como "última fala" para a ação *repetir* | **Não** (some ao fechar o app) |
| Configurações (verbosidade, limiar, liga/desliga detecção, nuvem, endpoint, modelo, tutorial concluído) | Usuário | `SharedPreferences` do app (armazenamento privado) | Sim, só preferências |
| `config.json` opcional (nuvem, endpoint, modelo) | Usuário, via `adb push` | `/sdcard/Android/data/com.pesquisa.visualassist/files/` | Sim, só configuração |

Verificado no código: a única compressão de imagem do app (`bitmap.compress` JPEG,
qualidade 80) escreve em `ByteArrayOutputStream` (memória) e serve apenas ao envio
da nuvem. Não há `FileOutputStream`, `MediaStore` nem gravação de bitmap em disco.

O app não tem contas, analytics próprios, publicidade nem telemetria própria.

## 3. Modo nuvem (opt-in): o que é enviado

Acontece somente se **todas** as condições forem verdadeiras: nuvem ligada em
Ajustes (ou `cloudEnabled=true` no `config.json`), endpoint configurado, rede
disponível e o usuário acionou "Descrever" (botão A ou painel).

- **Enviado** (`POST {endpoint}/v1/chat/completions`, JSON): 1 frame em JPEG
  (qualidade 80, até ~1280x960) codificado em base64, o prompt de descrição e o
  nome do modelo.
- **Recebido**: texto da descrição.
- **Destino**: o endpoint que **você** configurou. Pode ser um servidor local na
  mesma rede (protótipo: Ramalama + SmolVLM) ou qualquer serviço compatível com a
  API OpenAI. **Quem controla o endpoint recebe a imagem**; a retenção e o uso
  dela são responsabilidade desse servidor, não do app.
- **Sem criptografia no protótipo**: o manifest permite tráfego HTTP em texto
  claro (`usesCleartextTraffic=true`) para o endpoint local. Em rede não confiável,
  a imagem pode ser interceptada. Para qualquer uso fora de uma LAN confiável, use
  um endpoint **HTTPS** e remova o `usesCleartextTraffic` na versão publicada.
- **Terceiros na imagem**: o frame pode conter pessoas ao redor. Quem ativa a
  nuvem deve considerar isso (LGPD).
- **Falha de rede ou nuvem desligada**: o app não envia nada e descreve a cena
  localmente pelos objetos detectados.

## 4. Tráfego de rede fora do modo nuvem

Pelo código, estas são as únicas fontes de rede além do endpoint do LLM:

1. **Modelo de tradução EN→PT (ML Kit Translate).** O `SceneTranslator` é criado
   na abertura do app e chama `downloadModelIfNeeded`. Na **primeira execução** o
   modelo (~30 MB) é baixado, **mesmo com a nuvem desligada**; depois fica em
   cache. Nenhum dado do usuário é enviado nesse download. Há também a criação do
   cliente a cada abertura, o que pode gerar eventos de diagnóstico do ML Kit
   (item 5).
   - **Exceção ao "100% offline por padrão" (Requisito 7.4):** a primeira
     execução exige rede por esse motivo, e o tradutor só é necessário no modo
     nuvem. Ver "Pendências".
2. **Diagnóstico do ML Kit** (item 5).

Todo o resto roda localmente: detecção de objetos (MediaPipe, modelo embarcado),
OCR (ML Kit, versão *bundled* com modelo embarcado) e voz (sherpa-onnx, modelo
embarcado).

## 5. Telemetria de bibliotecas de terceiros

Segundo a documentação oficial do ML Kit
(<https://developers.google.com/ml-kit/android-data-disclosure>, atualizada em
14/05/2025), os SDKs coletam dados de diagnóstico e uso: informações do
dispositivo (fabricante, modelo, versão do SO), nome do pacote e versão do app,
identificadores **por instalação** (não destinados a identificar unicamente o
usuário), métricas de desempenho, configuração da API, tamanho de entrada/saída,
tipos de evento e códigos de erro. Os dados são enviados por HTTPS e, segundo o
Google, não são repassados a terceiros. Para o `translate`, também são coletados
os idiomas configurados e usados o Firebase Remote Config e o Firebase
Installations.

Essa lista **não inclui o conteúdo de imagens ou textos** processados. Ainda assim,
o app usa `mlkit-text-recognition` e `mlkit-translate`, então esse tipo de
diagnóstico é um fluxo de dados potencial e deve constar em qualquer declaração de
privacidade da publicação.

## 6. Conformidade e política da plataforma

- **LGPD**: o app não identifica pessoas, não persiste imagens e mantém o envio
  externo sob consentimento explícito. O titular do endpoint de nuvem é o
  controlador desse tratamento e responde pela base legal.
- **Meta Horizon OS**: imagens da câmera de passthrough são tratadas como *Device
  User Data* (Requisito 7.1). Antes de publicar na loja, revisar a política de
  dados da Meta para apps que usam a Passthrough Camera API e preencher a
  declaração de dados exigida.

## 7. O que NÃO foi verificado

- **Tráfego real do device.** Não foi capturado o tráfego de rede do app no Quest.
  As afirmações acima vêm da leitura do código e da documentação do ML Kit. Falta
  confirmar com captura (por exemplo, `tcpdump` num roteador ou proxy) que, com a
  nuvem desligada e o modelo de tradução já em cache, não há conexões de saída.
- **Telemetria no Horizon OS.** O ML Kit documenta a coleta para Android com
  Google Play Services ou SDK *bundled*; não foi medido se, no Quest, o envio de
  diagnóstico de fato ocorre.
- **MediaPipe e sherpa-onnx.** Não foi auditada a ausência de telemetria nessas
  bibliotecas além da leitura do código do app.

## 8. Pendências para publicação

1. **Tradutor sob demanda**: criar o `SceneTranslator` somente quando a nuvem
   estiver ativa, para restaurar a primeira execução 100% offline (Req. 7.4).
2. **HTTPS obrigatório** fora de LAN: remover `usesCleartextTraffic` no build de
   publicação e validar certificado do endpoint.
3. **Captura de tráfego** para validar a seção 7.
4. **Declaração de dados da loja** (Meta) e política de privacidade pública
   baseada neste documento.
