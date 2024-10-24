(ns harja.views.hallinta.rahavaraukset
  (:require [tuck.core :refer [tuck]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot]))

(defn onko-dis? [rivi]
  (if (= -1 (:id rivi))
    false
    true))

(defn rahavaraukset* [e! _app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeRahavaraukset)))
    (fn [e! {:keys [valittu-urakka urakat urakoiden-rahavaraukset
                    rahavaraukset tallennus-kesken? haku-kaynnissa?] :as app}]
      (let [valitun-urakan-rahavaraukset (filter #(= (:urakka-id %) (:urakka-id valittu-urakka))
                                           urakoiden-rahavaraukset)
            ;; Merkitään, onko valittu
            muokatut-rahavaraukset (map
                                     (fn [rahavaraus]
                                       (-> rahavaraus
                                         (assoc :valittu? (some #(= (:id %) (:id rahavaraus)) valitun-urakan-rahavaraukset))
                                         (assoc :urakkakohtainen-nimi (:urakkakohtainen-nimi (first (filter #(= (:id %) (:id rahavaraus)) valitun-urakan-rahavaraukset))))))
                                     rahavaraukset)]
        [:div.rahavaraukset-hallinta
         [:h1 "Rahavaraukset"]
         [debug/debug app]

         ;; Urakan valinta
         [yleiset/pudotusvalikko
          "Urakka"
          {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
           :valinta valittu-urakka
           :format-fn :urakka-nimi}
          urakat]

         [yleiset/info-laatikko :vahva-ilmoitus
          (str
            "Rahavarauksen poistaminen poistaa sen kaikki tehtävät sekä se poistetaan kaikilta urakoilta. "
            "Poisto ei ole mahdollinen, jos rahavaraus on käytössä. "
            "Älä siis yritäkään poistaa niitä, ellet ole täysin varma, että mitä olet tekemässä.")]

         ;; Jos haku tai tallennus käynnissä, näytä hyrrä
         (if (or tallennus-kesken? haku-kaynnissa?)
           [:div.ajax-loader-valistys
            [ajax-loader-pieni (str "Haetaan tietoja...")]]

           [grid/grid
            {:tyhja "Ei rahavarauksia."
             :tunniste :id
             :piilota-toiminnot? false
             :tallenna-vain-muokatut true
             :tallenna (fn [muokatut-rivit _arvo]
                         ;; Jos rivejä poistettiin, varmista käyttäjältä halutaanko näin tehdä
                         (if (some #(= true (:poistettu %)) muokatut-rivit)
                           ;; Rivejä poistettiin, varmista käyttäjältä
                           (do
                             (varmista-kayttajalta
                               {:otsikko "Rahavarauksia poistettiin, oletko varma että haluat tallentaa?"
                                :sisalto (str "Rahavarauksen poistaminen poistaa sen kaikki tehtävät sekä se poistetaan kaikilta urakoilta. Poisto ei ole mahdollinen, jos rahavaraus on käytössä.")
                                :hyvaksy "Kyllä"
                                :toiminto-fn #(tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaRahavaraus valittu-urakka muokatut-rivit)})
                             ;; Tallenna funktion pitää aina palauttaa kanava, passaa muokkaa funktiolle nil
                             (tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaRahavaraus valittu-urakka nil))

                           ;; Rivejä ei poistettu, varmistusta ei tarvitse
                           (tuck-apurit/e-kanavalla! e! tiedot/->MuokkaaRahavaraus valittu-urakka muokatut-rivit)))
             :uusi-rivi (fn [rivi]
                          (assoc rivi :id -1 :valittu? nil :nimi "" :urakkakohtainen-nimi ""))}
            [;; Muokkausgridi ei toimi default checkboxin kanssa. Se ei saa on-rivi-blur toimintaan checkboxin oikeaan arvoa
             ;; Joten tehdään oma komponentti, jossa ohitetaan on-rivi-blur toiminta ihan erillisellä kutsulla
             {:otsikko "" :nimi :valittu? :tyyppi :komponentti :leveys 1
              :komponentti (fn [rivi {:keys [muokataan?]}]
                             (let [id (gensym "rahavaraus")]
                               [:span.rahavaraus-valinta
                                ;; Halutaan piilottaa tämä checkbox kun muokkaus on päällä
                                (when-not muokataan?
                                  [:input.vayla-checkbox
                                   {:type :checkbox
                                    :id id
                                    :checked (boolean (:valittu? rivi))
                                    :on-change #(do
                                                  (.preventDefault %)
                                                  (.stopPropagation %)
                                                  (e! (tiedot/->ValitseUrakanRahavaraus valittu-urakka rivi
                                                        (-> % .-target .-checked))))}])
                                [:label {:for id} ""]]))}
             {:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys 10}
             {:otsikko "Urakkakohtainen nimi" :nimi :urakkakohtainen-nimi :tyyppi :string :leveys 10 :muokattava? #(onko-dis? %)}]
            muokatut-rahavaraukset])]))))

(defn rahavaraukset []
  [tuck tiedot/tila rahavaraukset*])
