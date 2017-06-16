(ns harja.views.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.ui.debug :refer [debug]]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.tiedot.urakka :as u]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.turvalaite :as tu]))

(defn kiintion-toimenpiteet [e! app kiintio]
  [grid/grid
   {:tyhja "Ei liitettyjä toimenpiteitä"
    :tunniste ::to/id}
   [{:otsikko "Työluokka" :nimi ::to/tyoluokka :fmt to/reimari-tyoluokka-fmt :leveys 10}
    {:otsikko "Toimenpide" :nimi ::to/toimenpide :fmt to/reimari-toimenpidetyyppi-fmt :leveys 10}
    {:otsikko "Päivämäärä" :nimi ::to/pvm :fmt pvm/pvm-opt :leveys 10}
    {:otsikko "Turvalaite" :nimi ::to/turvalaite :leveys 10 :hae #(get-in % [::to/turvalaite ::tu/nimi])}]
   (::kiintio/toimenpiteet kiintio)])

(defn kiintiot* [e! app]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->PaivitaValinnat
                                 {:urakka-id (:id @nav/valittu-urakka)
                                  :sopimus-id (first @u/valittu-sopimusnumero)}))
                           (e! (tiedot/->HaeKiintiot)))
                      #(do (e! (tiedot/->Nakymassa? false))))
    (fn [e! {:keys [kiintiot] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      [:div
       [debug app]
       [valinnat/urakan-sopimus @nav/valittu-urakka]
       [grid/grid
        {:otsikko ""
         :voi-poistaa? (constantly false) ;;TODO oikeustarkastus + pitää olla tyhjä
         :piilota-toiminnot? true ;; TODO
         :voi-lisata? true
         :tallenna #(e! (tiedot/->TallennaKiintiot))
         :tyhja "Ei määriteltyjä kiintiöitä"
         :jarjesta ::kiintio/nimi
         :tunniste ::kiintio/id
         :uusi-rivi (fn [rivi] rivi)
         :vetolaatikot (into {}
                             (map (juxt ::kiintio/id (fn [rivi] [kiintion-toimenpiteet e! app rivi])))
                             kiintiot)
         }
          [{:tyyppi :vetolaatikon-tila :leveys 3}
           {:otsikko "Nimi"
            :nimi ::kiintio/nimi
            :tyyppi :string
            :leveys 3}
           {:otsikko "Kuvaus"
            :nimi ::kiintio/kuvaus
            :tyyppi :text
            :leveys 3}
           {:otsikko "Koko"
            :nimi ::kiintio/koko
            :tyyppi :positiivinen-numero
            :leveys 3}
           {:otsikko "Toteutunut"
            :nimi :toteutunut
            :hae (constantly 0)
            :tyyppi :positiivinen-numero
            :muokattava? (constantly false)
            :leveys 3}]
          kiintiot]])))

(defn kiintiot []
  [tuck/tuck tiedot/tila kiintiot*])
