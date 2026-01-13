# openHAB 5.1 binding: ukrainesvitlo

This binding is a port of the Home Assistant custom component **ha-svitlo-yeah**:
https://github.com/ALERTua/ha-svitlo-yeah/
It is intended to help people in Ukraine plan around blackout schedules during the ongoing war.
Russia's attacks on Ukraine's energy infrastructure have caused prolonged outages, and this binding aims to provide
timely schedule data to help the brave Ukrainian people cope with blackouts.

Thank you to the authors and maintainers of the original HA extension for their work and data model inspiration.

## Thing types

- `ukrainesvitlo:service` (bridge)
- `ukrainesvitlo:region` (outage schedule)

## Providers

- `yasno`
- `dtek_json`
- `e-svitlo`

## Usage

Create a Bridge `ukrainesvitlo:service`, then a Thing `ukrainesvitlo:region` with config:

- region: `kyiv` (etc)
- provider: `yasno`
- group: `3.1`
- refreshSeconds: `300`

Channels:
- electricity: `connected|planned_outage|emergency`
- nextPlannedOutage, nextScheduledOutage, nextConnectivity
- plannedOutagesJson, scheduledOutagesJson
- dataChanged trigger (fires on schedule change)

### Example `.things`

```
Bridge ukrainesvitlo:service:main [ refreshSeconds=300, httpTimeoutMs=8000 ]

Thing ukrainesvitlo:region:kyiv (ukrainesvitlo:service:main) [
  region="kyiv",
  provider="yasno",
  group="3.1",
  refreshSeconds=300
]
```

### Example items

```
String  UkrSvitlo_Electricity        "Electricity [%s]" { channel="ukrainesvitlo:region:kyiv:electricity" }
DateTime UkrSvitlo_NextPlanned       "Next planned [%1$tF %1$tR]" { channel="ukrainesvitlo:region:kyiv:nextPlannedOutage" }
DateTime UkrSvitlo_NextConnectivity  "Next power [%1$tF %1$tR]" { channel="ukrainesvitlo:region:kyiv:nextConnectivity" }
String  UkrSvitlo_PlannedJson        "Planned JSON" { channel="ukrainesvitlo:region:kyiv:plannedOutagesJson" }
```

## Configuration

Bridge parameters:
- `refreshSeconds` (optional): default refresh interval for region things.
- `httpTimeoutMs` (optional): HTTP timeout in milliseconds.

Region parameters:
- `region` (required): region key (see provider section).
- `provider` (required): `yasno`, `dtek_json`, or `e-svitlo`.
- `group` (required for `yasno` and `dtek_json`): group identifier like `3.1`.
- `username`/`password`/`accountId` (e-svitlo only): credentials and optional account id.
- `refreshSeconds` (optional): per-thing override.

## Provider notes

### Yasno

- Uses Yasno public API.
- `region` must match a Yasno region name or key.

Get available regions:
1. Set log level to DEBUG for `org.openhab.binding.ukrainesvitlo`.
2. Use an invalid `region` value for a Yasno thing.
3. Check the log for "Yasno available regions: [...]".

### DTEK (JSON)

- Uses outage-data-ua / OE_OUTAGE_DATA JSON sources.
- `region` must match a key in `ProviderData.DTEK_PROVIDER_URLS`.

Get available regions:
1. Set log level to DEBUG for `org.openhab.binding.ukrainesvitlo`.
2. Use an invalid `region` value for a DTEK thing.
3. Check the log for "DTEK available regions: [...]".

### E-Svitlo

- Requires `username` and `password`.
- `accountId` is optional; when omitted, the first account is used.

## Troubleshooting

- If a thing is `OFFLINE`, check the thing status details and openHAB logs for error messages.
