(ns harja.views.tilannekuva.tienakyma
  "Tienäkymä tilannekuvaan.

  Tienäkymä (aka 'supernäkymä') mahdollistaa tietyn tieosan
  ja aikavälin valinnan ja hakee kaiken mitä kyseisellä tiellä
  on tapahtunut kyseisellä aikavälillä.

  Tämä on tärkeä käyttäjille kun tutkitaan vaikka onnettomuutta.
  Silloin halutaan saada tietyltä ajalta kaikki mitä kyseisellä
  tieosuudella on tehty näkyviin (auraukset, tarkastukset, ilmoitukset jne).

  Tienäkymässä on oma lomakkeensa hakuparametrien syöttämistä varten
  (valinnat)."

  (:require [harja.tiedot.tilannekuva.tienakyma :as tiedot]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta.infopaneeli :as infopaneeli]))

(defn- valinnat
  "Valintalomake tienäkymälle."
  [e! {:keys [valinnat haku-kaynnissa?] :as app}]
  (log "e! " e!)
  [lomake/lomake
   {:otsikko "Tarkastele tien tietoja"
    :muokkaa! #(e! (tiedot/->PaivitaValinnat %))
    :footer [:div.inline
             [napit/yleinen
              "Hae"
              #(e! (tiedot/->Hae))
              {:ikoni (ikonit/livicon-search)}]
             (when haku-kaynnissa?
               [yleiset/ajax-loader "Haetaan tietoja..."])]
    :ei-borderia? true}
   [{:nimi :tierekisteriosoite :tyyppi :tierekisteriosoite
     :tyyli :rivitetty
     :sijainti (r/wrap (:sijainti valinnat)
                       #(e! (tiedot/->PaivitaSijainti %)))
     :otsikko "Tierekisteriosoite"
     :palstoja 3}
    {:nimi :alku :tyyppi :pvm-aika
     :otsikko "Alkaen" :palstoja 3}
    {:nimi :loppu :tyyppi :pvm-aika
     :otsikko "Loppuen" :palstoja 3}]
   valinnat])

(defn- tulospaneeli [e! tulokset]
  (komp/luo
   (komp/karttakontrollit
    :tienakyma-tulokset
    ^{:class "kartan-infopaneeli"}
    [infopaneeli/infopaneeli [] #(log "infopaneeli pois")
     {}])
   (fn [e! tulokset]
     [:span.tienakyma-tulokset])))

(defn- tienakyma* [e! app]
  (komp/luo
   (komp/sisaan-ulos #(e! (tiedot/->Nakymassa true))
                     #(e! (tiedot/->Nakymassa false)))
   (fn [e! {:keys [tulokset] :as app}]
     (log "1. e! " e!)
     [:span
      [valinnat e! app]
      (when tulokset
        [tulospaneeli e! tulokset])])))

(defn tienakyma
  []
  [tuck/tuck tiedot/tienakyma tienakyma*])
