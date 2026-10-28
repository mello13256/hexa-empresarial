# Hexa Empresarial Edition

Launcher Android premium, independente, com a interface inteiramente em português de Portugal.
A peça central é a **gaveta orbital**: anéis concêntricos com as aplicações distribuídas sem
sobreposições, com centro configurável. Também há uma gaveta em **grelha** convencional, que se
abre com o botão quadrado.

## Funcionalidades

**Ecrã inicial**
- Páginas horizontais: criar, reordenar e eliminar páginas, e definir a página principal.
- Grelha de 3 a 7 colunas e de 4 a 9 linhas. Ao reduzir a grelha, nenhum elemento é perdido.
- Arrastar elementos entre células, entre páginas e para a dock.
- Modo de edição com toque prolongado.
- Relógio com data e dia da semana, em formato de 12 h ou 24 h e com cartão opcional.
- Barra de pesquisa.
- Fundo: imagem do sistema, gradiente executivo, cor sólida ou imagem própria.

**Dock**
- De 3 a 7 posições, com tamanho, espaçamento, margem, fundo e etiquetas configuráveis.
- Estilos: medalhão dourado, medalhão prateado ou ícones originais.
- Botão quadrado opcional para abrir a grelha.

**Gaveta orbital**
- Geometria configurável: número de órbitas, espaçamento, raio máximo, tamanho dos ícones e do centro, e distância mínima entre ícones.
- Quando as aplicações não cabem, os ícones reduzem até um mínimo; depois disso, as aplicações passam para páginas adicionais.
- Aparência configurável: cor e opacidade das linhas, anéis decorativos, fundo, fundo dos ícones e sombras.
- Animação de abertura em três tempos (primeiro os anéis, depois os ícones, por fim o centro), em estilo radial, de perspetiva ou de desvanecimento.
- Organização: favoritos, aplicações prioritárias, ocultas, e distribuição automática ou manual.

**Gaveta em grelha**
- Ordem alfabética (com regras do português), por utilização ou manual.
- Número de colunas configurável.
- A gaveta lembra a última vista usada, orbital ou em grelha.

**Pesquisa**
- Ignora acentos.
- Ordena os resultados por: correspondência exata, início do nome, início de palavra, iniciais, conteúdo.

**Grupos**
- Formas: círculo, radial, hexágono, grelha ou espiral.
- Configuráveis: nome, elemento central (símbolo, ícone de aplicação, imagem ou nenhum), tamanho, modo de abertura e velocidade.

**Widgets reais (`AppWidgetHost`)**
- Adicionar, posicionar, redimensionar e remover.
- Ficam guardados entre reinícios.

**Gestos**
- Deslizar para cima, deslizar para baixo, toque duplo e botão inicial.
- As ações do sistema (notificações, definições rápidas, recentes, bloquear) só funcionam depois de o utilizador ativar o serviço de acessibilidade. Não há simulações.

**Aparência**
- Tema claro, escuro ou automático.
- Cor de destaque, contraste, opacidade das superfícies e arredondamento.
- Logótipo opcional.

**Definições**
- 12 categorias, com pré-visualizações onde é possível.

**Cópias de segurança**
- Exportação e importação através do seletor de ficheiros do sistema (SAF).
- O ficheiro é validado antes de ser aplicado, e é pedida confirmação antes de substituir a configuração atual.

## Estrutura

```
domain/   Kotlin puro, sem Android: modelo, motor da grelha, geometria orbital e dos grupos,
          ordenação e pesquisa, persistência atómica com recuperação, formato das cópias de segurança.
app/      Android + Jetpack Compose.
  apps/       catálogo (LauncherApps, perfis de trabalho), ícones e pacotes de ícones, arranque
  home/       ecrã inicial, dock, edição, arrastar e largar, menus
  drawer/     gaveta orbital e em grelha, pesquisa
  groups/     grupos e editor
  widgets/    AppWidgetHost e seletor de widgets
  settings/   12 categorias e cópias de segurança
  system/     serviço de acessibilidade opcional
  theme/      sistema de design (cores, tipografia, tokens, logótipo)
```

## Compilar e testar

```
./gradlew :domain:test                       # testes do domínio e verificação linguística
./gradlew :app:testDebugUnitTest             # testes Robolectric (manifesto, idioma, fluxos de UI)
./gradlew :app:assembleDebug :app:assembleRelease
```

O GitHub Actions corre os testes e as compilações em cada push e publica os APK como artefacto
(`hexa-empresarial-apks`). A versão release é otimizada (R8) e assinada com a chave de depuração,
para poder ser instalada diretamente.

## Limitações conhecidas

- **Widgets nas cópias de segurança:** os identificadores dos widgets pertencem à instalação que os criou. Depois de importar uma cópia, cada widget aparece no seu lugar e basta tocar para o voltar a adicionar.
- **Imagens:** as imagens próprias (fundo e elementos centrais) não são incluídas nas cópias de segurança.
- **Atalhos das aplicações:** o Android só os disponibiliza ao launcher predefinido.
- **Ações do sistema:** notificações, definições rápidas, aplicações recentes e bloqueio do ecrã requerem a ativação manual do serviço de acessibilidade. Sem ele, estas opções explicam o que falta em vez de falharem em silêncio.
- **Ícones monocromáticos:** usam o ícone temático da aplicação no Android 13 ou posterior. Nas versões anteriores, ou sem ícone temático, o ícone é dessaturado.
- **Testes:** a aplicação foi verificada com testes automáticos (JVM e Robolectric) e compilada no CI, mas não foi testada num dispositivo físico. Fluxos que dependem do sistema não têm teste automático: atribuição de widgets, seletor de ficheiros, função de ecrã principal e serviço de acessibilidade.
