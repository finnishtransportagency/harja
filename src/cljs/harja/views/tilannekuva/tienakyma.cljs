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
            [harja.views.kartta.infopaneeli :as infopaneeli]
            [harja.tiedot.kartta :as kartta-tiedot]))

(defn- valinnat
  "Valintalomake tienäkymälle."
  [e! {:keys [valinnat haku-kaynnissa? tulokset] :as app}]
  [lomake/lomake
   {:otsikko "Tarkastele tien tietoja"
    :muokkaa! #(e! (tiedot/->PaivitaValinnat %))
    :footer-fn (fn [data]
                 [:span
                  [napit/yleinen
                   "Hae"
                   #(e! (tiedot/->Hae))
                   {:ikoni (ikonit/livicon-search)
                    :disabled (not (lomake/validi? data))}]
                  (when haku-kaynnissa?
                    [yleiset/ajax-loader "Haetaan tietoja..." {:luokka "inline-block"}])])
    :ei-borderia? true}
   [{:nimi :tierekisteriosoite :tyyppi :tierekisteriosoite
     :tyyli :rivitetty
     :pakollinen? true
     :sijainti (when-not tulokset
                 (r/wrap (:sijainti valinnat)
                         #(e! (tiedot/->PaivitaSijainti %))))
     :otsikko "Tierekisteriosoite"
     :palstoja 3}
    {:nimi :alku :tyyppi :pvm-aika :pakota-suunta :ylos-vasen
     :pakollinen? true
     :otsikko "Alkaen" :palstoja 3}
    {:nimi :loppu :tyyppi :pvm-aika :pakota-suunta :ylos-vasen
     :pakollinen? true
     :otsikko "Loppuen" :palstoja 3}]
   valinnat])

(defn- nayta-tulospaneeli! [e! tulokset avatut-tulokset]
  (kartta-tiedot/nayta-kartan-kontrollit!
   :tienakyma-tulokset
   ^{:class "kartan-infopaneeli"}
   [infopaneeli/infopaneeli-komponentti
    {:avatut-asiat (comp avatut-tulokset :idx :data)
     :toggle-asia! #(e! (tiedot/->AvaaTaiSuljeTulos (:idx (:data %))))
     :piilota-fn! #(e! (tiedot/->SuljeInfopaneeli))
     :linkkifunktiot {:toteuma {:teksti "Tarkastele toteumanäkymässä"
                                :toiminto #(e! (tiedot/->TarkasteleToteumaa %))}}
     :ei-tuloksia [:span "Hakuehdoilla ei löytynyt tuloksia"]}
    tulokset]))

(defn- tulospaneeli [e! tulokset avatut-tulokset]
  (komp/luo
   (komp/sisaan-ulos #(reset! kartta-tiedot/ikonien-selitykset-sijainti :vasen)
                     #(reset! kartta-tiedot/ikonien-selitykset-sijainti :oikea))
   (komp/sisaan-ulos #(nayta-tulospaneeli! e! tulokset avatut-tulokset)
                     #(kartta-tiedot/poista-kartan-kontrollit! :tienakyma-tulokset))
   (komp/kun-muuttuu nayta-tulospaneeli!)
   (fn [_ _ _]
     [:span.tienakyma-tulokset])))

(defn- tienakyma* [e! app]
  (komp/luo
   (komp/sisaan-ulos #(e! (tiedot/->Nakymassa true))
                     #(e! (tiedot/->Nakymassa false)))
   (komp/ulos (kartta-tiedot/aseta-klik-kasittelija!
               (fn [{t :geometria}]
                 (when-let [idx (:idx t)]
                   (e! (tiedot/->AvaaTaiSuljeTulos (:idx t)))))))
   (fn [e! {:keys [tulokset avatut-tulokset] :as app}]
     [:span
      [valinnat e! app]
      (when tulokset
        [tulospaneeli e! tulokset avatut-tulokset])])))

(defn tienakyma
  []
  [tuck/tuck tiedot/tienakyma tienakyma*])
