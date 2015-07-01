Harja -järjestelmän julkinen API

API on jaettu neljään pääosioon:
<ol>
  <li>urakat:
    <ul>
      <li>Urakoiden perustietojen haku joko urakoitsijan id:llä tai yksittäisen urakan id:llä</li>
      <li>Urakan seurantatietojen kirjaus: toteumat, poikkeukset, tarkastukset ja havainnot</li>
    </ul>
  </li>
  <li>varusteet:
    <ul>
      <li>Varustetietojen haku Tierekisteristä</li>
      <li>Varustetietojen päivitys Tierekisteriin.</li>
    </ul>
  </li>
  <li>seuranta:
      <ul>
        <li>Reaaliaikaisten seurantatietojen, kuten esim. aura-auton liikkeiden kirjaus</li>
      </ul>
    </li>
  <li>ilmoitukset: 
    <ul>
      <li>Ilmoitusten haku urakoitsijakohtaisesti</li>
      <li>Ilmoitusten ilmoitusten kuittaaminen</li>
    </ul>   
  </li>
</ol>