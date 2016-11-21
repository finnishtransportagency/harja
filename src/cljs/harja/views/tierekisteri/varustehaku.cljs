(ns harja.views.tierekisteri.varustehaku
  "Tierekisterin varustehaun käyttöliittymä"
  (:require [harja.tiedot.tierekisteri.varusteet :as v]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.loki :refer [log]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]))

(defn varustehaku-ehdot [e! hakuehdot]
  [lomake/lomake
   {:otsikko "Hae varusteita Tierekisteristä"
    :muokkaa! #(e! (v/->AsetaVarusteidenHakuehdot %))
    :footer-fn (fn [rivi]
                 [napit/yleinen "Hae Tierekisteristä"
                  #(e! (v/->HaeVarusteita))
                  {:disabled (:haku-kaynnissa? hakuehdot)
                   :ikoni (ikonit/livicon-search)}])
    :tunniste (comp :tunniste :varuste)}

   [{:nimi :tietolaji
     :otsikko "Varusteen tyyppi"
     :tyyppi :valinta
     :pakollinen? true
     :valinnat (vec varusteet/tietolaji->selitys)
     :valinta-nayta second
     :valinta-arvo first}
    {:nimi :tierekisteriosoite
     :otsikko "Tierekisteriosoite"
     :tyyppi :tierekisteriosoite}
    {:nimi :tunniste
     :otsikko "Varusteen tunniste"
     :tyyppi :string}]
   hakuehdot])

(defn varustehaku-varusteet [e! tietolajin-listaus-skeema varusteet]
  [grid/grid
   {:otsikko "Tierekisteristä löytyneet varusteet"}
   tietolajin-listaus-skeema
   varusteet])

(defn varustehaku
  "Komponentti, joka näyttää lomakkeen varusteiden hakemiseksi tierekisteristä
  sekä haun tulokset."
  [e! {:keys [hakuehdot listaus-skeema tietolaji varusteet] :as app}]
  (log "HAKUEHDOT: " (pr-str hakuehdot))
  [:div.varustehaku
   [varustehaku-ehdot e! (:hakuehdot app)]
   (when (and listaus-skeema varusteet)
     [varustehaku-varusteet e! listaus-skeema varusteet])])
