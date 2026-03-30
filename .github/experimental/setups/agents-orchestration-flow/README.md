# agents-orchestration-flow

- Tämä kansio sisältää agenttien orkestrointimallin, jonka voit ottaa käyttöön kopioimalla tiedostot omaan IDE:n agenttipolkuun, .copilot/agents kansioon tai osoittamalla VS Coden `chat.agentFilesLocations`-asetuksen tähän kansioon.
- Pääset nopeasti alkuun valitsemalla isoon kokonaisuuteen `00-orchestrate-delivery`-agentin ja pieneen muutokseen `01-orchestrate-small-change`-agentin.
- Flow-agentteja voi käyttää myös itsenäisesti, esimerkiksi review- ja verify-tehtävissä.
- Käytä `support-agent-format`-agenttia, kun muokkaat tai luot agenttitiedostoja ja haluat säilyttää yhteisen rakenteen.

## Kansiorakenne

- `agents/` sisältää varsinaiset agentit ja promptit.
- `references/` sisältää yhteiset rakennesäännöt ja käytännöt.
- `domain/` sisältää Harja-kohtaisen taustatiedon agenteille.
