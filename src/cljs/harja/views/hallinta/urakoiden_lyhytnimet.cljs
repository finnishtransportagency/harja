(ns harja.views.hallinta.urakoiden-lyhytnimet
  "Urakoiden lyhytnimien näkymä. Täällä jvh pitää yllä urakoiden lyhytnimiä."
  (:require
    [tuck.core :refer [tuck]]

    [harja.ui.grid :as grid]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.hallinta.yhteiset :as yhteiset]
    [harja.tiedot.hallinta.urakoiden-lyhytnimet :as tiedot]
    [harja.tiedot.navigaatio :as nav]
    [harja.ui.kentat :as kentat]
    [harja.ui.komponentti :as komp]))

(defn- urakan-tilan-nimi
  [tila-str]
  (case tila-str
    "meneillaan-tai-tuleva"
    "Meneillään olevat tai tulevat"
    "paattyneet"
    "Päättyneet"))

(defn- urakoiden-lyhytnimet* [e! app]
  (komp/luo
    (komp/sisaan
      #(e! (tiedot/->HaeUrakoidenLyhytnimet {:urakkatyyppi @yhteiset/valittu-urakkatyyppi})))
    (fn [e! {:keys [urakoiden-nimet hae-urakoiden-lyhytnimet-kesken? hae-urakoiden-lyhytnimet-kesken?] :as app}]
      (let [urakkatyypit (keep #(if (not= :vesivayla (:arvo %))
                                  %
                                  {:nimi "Vesiväylät" :arvo :vesivayla-hoito})
                           (conj nav/+urakkatyypit-ja-kaikki+
                             {:nimi "Kanavat", :arvo :vesivayla-kanavien-hoito}))
            ]
        [:div
         [:div.lyhytnimet
          [:h3 "Urakkanimet"]
          [:div.flex-row
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Urakan tila"]
            [yleiset/livi-pudotusvalikko {:valinta (:urakan-tila app)
                                          :format-fn #(if % (urakan-tilan-nimi %) "Kaikki urakat")
                                          :valitse-fn #(e! (tiedot/->PaivitaUrakanTila %))}
             [nil "meneillaan-tai-tuleva" "paattyneet"]]]
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Urakkatyypit"]
            [yleiset/livi-pudotusvalikko {:valinta (:valittu-urakkatyyppi app)
                                          :format-fn :nimi
                                          :valitse-fn #(e! (tiedot/->PaivitaValittuUrakkaTyyppi %))}
             urakkatyypit]]
           [kentat/raksiboksi {:teksti "Näytä vain urakat joilta lyhyt nimi puuttuu"
                               :toiminto (fn [arvo]
                                           (e! (tiedot/->PaivitaVainPuuttuvat (-> arvo .-target .-checked))))}
            (:vain-puuttuvat app)]
           ]

          [:div
           [harja.ui.debug/debug {:app app}]
           [:div
            [grid/grid
             {:otsikko "Urakoiden lyhyet nimet"
              :voi-muokata? true
              :voi-lisata? false
              :voi-poistaa? (constantly false)
              :piilota-toiminnot? false
              :tunniste :id
              :jarjesta :nimi
              :tallenna-vain-muokatut true
              :tallenna (fn [urakat]
                          (e! (tiedot/->PaivitaUrakoidenLyhytnimet {:urakat urakat})))}

             [{:nimi :nimi
               :leveys "auto"
               :otsikko "Virallinen nimi"
               :tyyppi :string
               :muokattava? (constantly false)}
              {:nimi :lyhyt_nimi
               :leveys "auto"
               :otsikko "Lyhyt nimi"
               :tyyppi :string}]

             (:UrakoidenLyhytnimet app)]]]]]))))
(defn urakoiden-lyhytnimet [e! app]
  [tuck tiedot/tila urakoiden-lyhytnimet*])
