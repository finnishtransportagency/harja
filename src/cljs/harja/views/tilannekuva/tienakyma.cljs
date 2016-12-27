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
            [harja.ui.komponentti :as komp]))

(defn valinnat*
  "Valintalomake tienäkymälle."
  [e! app]
  (komp/luo
   (komp/sisaan-ulos #(e! (tiedot/->Nakymassa true))
                     #(e! (tiedot/->Nakymassa false)))
   (fn [e! {:keys [valinnat haku-kaynnissa?] :as app}]
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
      valinnat])))

(defn valinnat
  []
  [tuck/tuck tiedot/tienakyma valinnat*])
