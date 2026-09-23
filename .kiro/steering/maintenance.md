---
inclusion: always
---
# Manutenção de documentação e versionamento

A CADA nova implementação (feature) ou correção (fix) neste projeto, siga
este checklist antes de considerar a tarefa concluída:

## 1. Código e testes
- Implemente separando lógica pura (testável) de código Android.
- Adicione/atualize testes JUnit da lógica pura.
- Rode build + testes na distrobox `quest-dev` (ver `build.md`).

## 2. Atualizar a documentação (OBRIGATÓRIO)
- **`docs/TECHNICAL.md`**:
  - Se for FEATURE: mova o item no Roadmap (seção 6) de ⏳ para ✅, e descreva-a
    na seção 3 (Funcionalidades implementadas) com os arquivos envolvidos.
    Atualize a tabela de tecnologias (seção 4) se entrou lib/modelo novo.
  - Se for FIX: registre em "Limitações conhecidas" (seção 6) quando pertinente,
    e ajuste a seção afetada.
  - Atualize a data de "Última atualização" no topo.
- **`README.md`**: atualize a lista de funcionalidades e instruções se o uso mudou.
- **`.kiro/specs/visual-assist/tasks.md`**: marque a subtarefa como `[x]`.

## 3. Versionamento (o agente cuida dos commits — avise antes)
- Commit incremental com mensagem descritiva (o que + por quê).
- `git push` para manter o GitHub (danielslz/quest-assist-app) atualizado.
- Uma tarefa/fix por commit quando possível.

## Formato da mensagem de commit
```
<Tarefa/Fix>: <resumo curto>

- o que mudou (bullets)
- por que / evidência (ex.: testes, validação no device)
```

## Regra de ouro
Documentação desatualizada é bug. Se o código mudou o comportamento, a doc
(`TECHNICAL.md` + `README.md`) DEVE refletir isso no mesmo commit.
