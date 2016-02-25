(ns harja.ui.openlayers.taso
  "Määrittelee karttatason kaltaisen protokollan"
  (:require [harja.ui.openlayers.featuret :as featuret]
            [harja.loki :refer [log]]))

(defprotocol Taso
  (aseta-z-index [this z-index]
                 "Palauttaa uuden version tasosta, jossa zindex on asetettu")
  (extent [this] "Palauttaa tason geometrioiden extentin [minx miny maxx maxy]")
  (selitteet [this] "Palauttaa tällä tasolla olevien asioiden selitteet")
  (paivita
   [this ol3 ol-layer aiempi-paivitystieto]
   "Päivitä ol-layer tai luo uusi layer. Tulee palauttaa vektori, jossa on
ol3 Layer objekti ja tälle tasolle spesifinen päivitystieto. Palautettu
päivitystieto annettaan seuraavalla kerralla aiempi-paivitystieto
parametrina takaisin. Jos päivitys luo uuden ol layerin, tulee
sen lisätä se itse ol3 karttaan (addLayer)"))
