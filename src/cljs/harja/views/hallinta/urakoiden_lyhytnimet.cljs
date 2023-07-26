(ns harja.views.hallinta.urakoiden-lyhytnimet
  "Urakoiden lyhytnimien näkymä. Täällä jvh pitää yllä urakoiden lyhytnimiä."
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]
             [cljs.core.async :refer [<! >! chan]]

             [harja.ui.grid :as grid]
             [harja.ui.napit :as napit]
             [harja.ui.ikonit :as ikonit]
             [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
             [harja.ui.valinnat :as valinnat]
             [harja.tiedot.hallinta.yhteiset :as yhteiset]
             [harja.tiedot.hallinta.urakoiden-lyhytnimet :as tiedot]
             [harja.tiedot.navigaatio :as nav]
             [harja.ui.lomake :as lomake]
             [harja.ui.kentat :as kentat]
             [harja.ui.viesti :as viesti]
             [harja.ui.komponentti :as komp]

             [harja.loki :refer [log tarkkaile!]]
             [harja.pvm :as pvm]
             [harja.domain.oikeudet :as oikeudet]))

;(defonce taulukon-virheet (atom nil))


(defn- urakan-tilan-nimi
  [hinnoittelu-str]
  (case hinnoittelu-str
    "meneillaan-tai-tuleva"
    "Meneillään olevat tai tulevat"
    "paattyneet"
    "Päättyneet"))

(defn- urakoiden-lyhytnimet* [e! app]
  (komp/luo
    (komp/sisaan
      #(e! (tiedot/->HaeUrakoidenLyhytnimet {:urakkatyyppi @yhteiset/valittu-urakkatyyppi})))
    (fn [e! {:keys [urakoiden-nimet hae-urakoiden-lyhytnimet-kesken? hae-urakoiden-lyhytnimet-kesken?] :as app}]
      (let [urakkatyyppi @yhteiset/valittu-urakkatyyppi
            urakkatyypit (keep #(if (not= :vesivayla (:arvo %))
                                  %
                                  {:nimi "Vesiväylät" :arvo :vesivayla-hoito})
                           (conj nav/+urakkatyypit+
                             {:nimi "Kanavat", :arvo :vesivayla-kanavien-hoito}))
            vain-puuttuvat? (atom false)
            urakan-tila (atom nil)]
        [:div
         [:div.lyhytnimet
          [:h3 "Urakkanimet"]
          [:div.rivi
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Urakan tila"]
            [yleiset/livi-pudotusvalikko {:valinta @urakan-tila
                                          :format-fn #(if % (urakan-tilan-nimi %) "Kaikki urakat")
                                          :valitse-fn #(reset! urakan-tila %)}
             [nil "meneillaan-tai-tuleva" "paattyneet"]]]
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Urakktyypit"]
            [yleiset/livi-pudotusvalikko {:valinta (:valittu-urakkatyyppi app)
                                          :format-fn :nimi
                                          :valitse-fn #(e! (tiedot/->PaivitaValittuUrakkaTyyppi %))}
             urakkatyypit]]
           #_[valinnat/urakkatyyppi
              yhteiset/valittu-urakkatyyppi
              urakkatyypit
              #(e! (tiedot/->PaivitaValittuUrakkaTyyppi %))
              #_(reset! yhteiset/valittu-urakkatyyppi %)
              ]
           [kentat/tee-kentta {:tyyppi :checkbox
                               :teksti "Näytä vain urakat joilta lyhyt nimi puuttuu"
                               }
            vain-puuttuvat?]
           ]
          (println "Urakan tila " @urakan-tila)
          (println "Urakkatyyppi " urakkatyyppi)
          (println "Urakkatyypit " urakkatyypit)]

         [:div
          [harja.ui.debug/debug {:app app}]
          [:div
           [grid/grid
            {:otsikko "Urakoiden lyhyet nimet"
             :voi-muokata? (constantly true)
             :voi-lisata? (constantly false)
             :voi-poistaa? (constantly false)
             :piilota-toiminnot? false
             :tunniste :id
             :tallenna-vain-muokatut true
             :tallenna (fn [urakat]
                         (e! (tiedot/->PaivitaUrakoidenLyhytnimet {:urakat urakat :urakkatyyppi @yhteiset/valittu-urakkatyyppi})))}

            [{:nimi :nimi
              :leveys "auto"
              :otsikko "Virallinen nimi"
              :tyyppi :string}
             {:nimi :lyhyt_nimi
              :leveys "auto"
              :otsikko "Lyhyt nimi"
              :tyyppi :string}]

            (:UrakoidenLyhytnimet app)]]]]))))
(defn urakoiden-lyhytnimet [e! app]
  [tuck tiedot/tila urakoiden-lyhytnimet*])
