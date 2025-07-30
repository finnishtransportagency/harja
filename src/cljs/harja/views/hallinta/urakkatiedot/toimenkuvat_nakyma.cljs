(ns harja.views.hallinta.urakkatiedot.toimenkuvat-nakyma
  (:require [tuck.core :refer [tuck]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.urakkatiedot.toimenkuvat-tiedot :as tiedot]))

(defn onko-dis? [rivi]
  (if (= -1 (:id rivi))
    false
    true))

(defn toimenkuvat* [e! _app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeToimenkuvat)))
    (fn [e! {:keys [valittu-urakka urakat urakoiden-toimenkuvat
                    toimenkuvat tallennus-kesken? haku-kaynnissa?] :as app}]
      (let [valitun-urakan-toimenkuvat (filter #(= (:urakka-id %) (:urakka-id valittu-urakka)) urakoiden-toimenkuvat)
            ;; Merkitään, onko valittu
            muokatut-toimenkuvat (map
                                     (fn [toimenkuva]
                                       (-> toimenkuva
                                         (assoc :valittu? (some #(= (:nimi %) (:nimi toimenkuva)) valitun-urakan-toimenkuvat))
                                         (assoc :urakkakohtainen-nimi (:urakkakohtainen-nimi (first (filter #(= (:nimi %) (:nimi toimenkuva)) valitun-urakan-toimenkuvat))))))
                                   toimenkuvat)]
        [:div.toimenkuvien-hallinta
         [:h1 "Toimenkuvat"]
         [:p "Toimenkuvat ovat mh-urakoiden hallintaan liittyviä asioita, joilla voidaan tarkemmin määrittää, mihin
         urakassa rahoja budjetoidaan ja käytetaään. Alla olevasta urakkavalinnasta löytyy siis vain mh-urakat."]
         [:p "MH-urakoille on määritelty default toimenkuvia. Ne on asetettu suoraan tietokantaan. Tässä voit
         nimetä niitä uusiksi ja valita mitä toimenkuvia haluat käyttää urakassa."]
         ;; Urakan valinta
         [yleiset/pudotusvalikko
          "Urakka"
          {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
           :valinta valittu-urakka
           :format-fn :urakka-nimi}
          urakat]

         ;; Jos haku tai tallennus käynnissä, näytä hyrrä
         (if (or tallennus-kesken? haku-kaynnissa?)
           [ajax-loader-pieni "Haetaan tietoja..."]

           [grid/grid
            {:tyhja "Ei toimenkuvia."
             :tunniste :id
             :piilota-toiminnot? false
             :tallenna-vain-muokatut true
             :tallenna (fn [muokatut-rivit _arvo]
                         (tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaToimenkuva valittu-urakka muokatut-rivit))
             :uusi-rivi (fn [rivi]
                          (assoc rivi :id -1 :valittu? nil :nimi "" :urakkakohtainen-nimi ""))}
            [;; Muokkausgridi ei toimi default checkboxin kanssa. Se ei saa on-rivi-blur toimintaan checkboxin oikeaan arvoa
             ;; Joten tehdään oma komponentti, jossa ohitetaan on-rivi-blur toiminta ihan erillisellä kutsulla
             {:otsikko "" :nimi :valittu? :tyyppi :komponentti :leveys 1
              :komponentti (fn [rivi {:keys [muokataan?]}]
                             (let [id (gensym "toimenkuva")]
                               [:span.toimenkuva-valinta
                                ;; Halutaan piilottaa tämä checkbox kun muokkaus on päällä
                                (when-not muokataan?
                                  [:input.vayla-checkbox
                                   {:type :checkbox
                                    :id id
                                    :checked (boolean (:valittu? rivi))
                                    :on-change #(do
                                                  (.preventDefault %)
                                                  (.stopPropagation %)
                                                  (e! (tiedot/->ValitseUrakanToimenkuva valittu-urakka rivi
                                                        (-> % .-target .-checked))))}])
                                [:label {:for id} ""]]))}
             {:otsikko "Toimenkuva" :nimi :nimi :tyyppi :string :leveys 10}
             {:otsikko "Urakkakohtainen nimi" :nimi :urakkakohtainen-nimi :tyyppi :string :leveys 10 :muokattava? #(onko-dis? %)}]
            muokatut-toimenkuvat])
         [debug/debug app]]))))

(defn toimenkuvat []
  [tuck tiedot/tila toimenkuvat*])
