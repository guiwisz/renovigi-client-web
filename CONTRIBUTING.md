# Contribuindo

Obrigado pelo interesse! Algumas orientações para manter o projeto organizado.

## Antes de abrir um PR

- Rode os testes: `./gradlew :app:testDebugUnitTest`.
- Se mexer em widgets, confira **todos os tamanhos** na galeria de debug, também
  com os textos mais longos possíveis:
  `adb shell am start -n br.com.dhc.solar/.debug.WidgetGalleryActivity --es widget summary --ez stress true`.
  Nenhum texto pode aparecer cortado.
- Siga o estilo do código ao redor: Kotlin oficial e comentários em português,
  explicando o *porquê*.

## Outros inversores e contas

O app foi feito e testado com uma usina Renovigi (inversor monofásico,
`devcode 518`). Se a sua conta ou o seu inversor se comportar diferente:

1. Gere um dump **mascarado** das respostas com a `ExplorerActivity` (ver
   [docs/API.md](docs/API.md#explorando-outra-conta-ou-inversor)).
2. **Revise o conteúdo antes de anexar** a uma issue. A máscara remove números
   de série, contato e localização, mas confira mesmo assim.
3. Abra uma issue descrevendo o modelo do inversor e o que não funcionou.

## Segurança e privacidade

- Nunca inclua usuário, senha, token, número de série, endereço ou coordenadas
  em issues, commits ou capturas de tela. Para prints, use o **modo demonstração**.
- Ações de controle do inversor (liga/desliga, limite de potência etc.) não
  serão aceitas no app: um erro pode parar a geração ou desrespeitar o padrão
  da distribuidora.
