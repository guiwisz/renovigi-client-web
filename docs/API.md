# Integração com a nuvem ShineMonitor

Como o app conversa com a nuvem **ShineMonitor** (Eybond), a mesma usada pelo
RenoClient (Renovigi), pelo WatchPower e por outros apps de inversor. A API não é
oficialmente documentada para terceiros. O que está aqui veio da documentação
pública em <https://api.shinemonitor.com>, do portal web <https://www.shinemonitor.com>
e do projeto [shinemonitor-api](https://github.com/davidsmfreire/shinemonitor-api).
Os números dos exemplos são fictícios.

## Requisições

Todas são `GET` em `https://android.shinemonitor.com/public/`, com uma query
string assinada. A resposta tem sempre o formato:

```json
{ "err": 0, "desc": "ERR_NONE", "dat": { } }
```

`err ≠ 0` indica erro. Os mais comuns:

| Código | Significado |
|---|---|
| `0x0105` | usuário não encontrado (ver "Formas de login") |
| `0x0010` | senha incorreta |
| `0x010E` | token expirado: refaça o login |
| `0x000C` | sem dados para o período (ex.: curva antes do nascer do sol) |
| `0x0104` | usina não encontrada (ver "Contexto do app") |

Unidades padronizadas: potência em **kW** e energia em **kWh**. Muitos números
chegam como string (`"3.4210"`).

## Assinatura

- **Login:** `sign = SHA1(salt + SHA1(senha) + baseAction)`
- **Demais chamadas:** `sign = SHA1(salt + secret + token + baseAction)`

`salt` é o timestamp em milissegundos. `baseAction` é exatamente o trecho
`&action=…` enviado na URL, inclusive na mesma ordem. O SHA-1 da senha basta
para refazer o login, então o app não guarda a senha em si.

Implementação: [`ShineMonitorSigner.kt`](../app/src/main/java/br/com/dhc/solar/data/remote/ShineMonitorSigner.kt),
com testes em [`ShineMonitorSignerTest.kt`](../app/src/test/java/br/com/dhc/solar/data/remote/ShineMonitorSignerTest.kt).

## Formas de login

O backend aceita três ações. O app tenta nesta ordem e só passa para a próxima
quando a resposta é "usuário não encontrado":

| Ação | Quem usa | Observação |
|---|---|---|
| `authSource` | apps Android (WatchPower, RenoClient) | envia o sufixo `&_app_id_=…`, que restringe a busca às contas daquele app |
| `auth` | portal web | sem sufixo de app; usuário codificado com `encodeURIComponent` |
| `authEmail` | portal web (variante) | aceita usuário **ou e-mail** |

Todas usam `company-key=bnrl_frRFjEz8Mkn`, a chave pública que aparece nos
scripts do portal.

## Contexto do app (o sufixo `_app_id_`)

Os apps Android anexam `&i18n=…&lang=…&source=1&_app_client_=android&_app_id_=…&_app_version_=…`
a toda requisição. Em contas criadas pelo portal ou pelo RenoClient, quem entra
pela forma **web** (`auth`) só enxerga a usina **sem** esse sufixo: com ele,
`queryPlants` responde `0x0104`. O app guarda a forma de login junto com a
sessão (`Session.method`) e decide o sufixo a partir dela.

## Endpoints usados

### Usina
| Ação | Dados |
|---|---|
| `queryPlants`, `queryPlantInfo` | nome, status, potência instalada, fuso, coordenadas, tarifa (`profit.unitProfit`), fatores de CO₂/carvão |
| `queryPlantCurrentData` (`par=ENERGY_TODAY,ENERGY_MONTH,…,CURRENT_POWER`) | totais de hoje/mês/ano/total, potência atual, receita, CO₂ |
| `queryPlantActiveOuputPowerOneDay` | curva do dia (amostras a cada 5 min) |
| `queryPlantEnergyMonthPerDay` / `…YearPerMonth` / `…TotalPerYear` | histórico por dia, mês e ano |
| `queryPlantRunningNormalTime` | minutos de operação sem falhas |
| `editPlant` | **escrita** parcial: só os campos enviados mudam (o app usa `name` e `profit.unitProfit`) |

### Inversor e coletor
| Ação | Dados |
|---|---|
| `queryDevices` | PN do coletor, SN, `devcode` e `devaddr` do inversor |
| `queryDeviceLastData` | ~30 parâmetros ao vivo (títulos em inglês): tensão/corrente de cada MPPT, potências DC/AC, rede, temperatura, firmware… |
| `queryPlantDeviceChartsFieldsByType` | parâmetros que têm histórico |
| `queryDeviceChartFieldsDat` (`precision=5`) | série de um parâmetro num intervalo (288 pontos/dia; zeros onde não houve leitura) |
| `queryPlantSameDeviceParManyDay` | mesmo parâmetro em vários dias (comparação) |
| `queryDeviceWarning` | alarmes com código, descrição, início e fim |
| `queryCollectorInfo` | firmware e intervalo de envio (`datFetch`, normalmente 300 s) |

O campo `datFetch` define o "tempo real" possível: o coletor envia uma leitura
a cada ~5 minutos, e o app agenda a próxima busca para logo depois dela.

Também existem ações de **controle** (`queryDeviceCtrlField`, `ctrlDevice`), como
liga/desliga e limite de potência. O app **não** as usa de propósito: uma
mudança errada pode parar a geração ou desrespeitar o padrão exigido pela
distribuidora.

## Explorando outra conta ou inversor

A build de debug inclui ferramentas em `app/src/debug/`:

- **`ExplorerActivity`:** com uma sessão salva, chama os endpoints acima e grava
  as respostas em `Android/data/br.com.dhc.solar/files/explorer/`, com números
  de série, contato e localização **mascarados**.
  ```sh
  adb shell am start -n br.com.dhc.solar/.debug.ExplorerActivity
  adb pull /sdcard/Android/data/br.com.dhc.solar/files/explorer
  ```
- **`LiveProbeTest`:** testa login e endpoints pela JVM com a sua conta, via `scripts/probe.sh`.

Se o seu inversor devolver títulos de parâmetro diferentes, o ponto de ajuste é
[`InverterLive.fromParams`](../app/src/main/java/br/com/dhc/solar/domain/Inverter.kt).
