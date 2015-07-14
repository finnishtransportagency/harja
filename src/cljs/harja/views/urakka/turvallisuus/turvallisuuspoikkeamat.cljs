(ns harja.views.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.kommentit :as kommentit])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]))

(defn turvallisuuspoikkeaman-tiedot
  []

  (let [muokattu (atom @tiedot/valittu-turvallisuuspoikkeama)
        lomakkeen-virheet (atom {})
        voi-tallentaa? (reaction (and
                                   (= (count @lomakkeen-virheet) 0)
                                   (> (count @muokattu) (count tiedot/+uusi-turvallisuuspoikkeama+))))]

    (fn []
      (run! @muokattu (log "Muokattu on nyt " (pr-str @muokattu)))
      [:div
       [:button.nappi-ensisijainen
        {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama nil)}
        "Palaa"]

       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! #(reset! muokattu %)
         :virheet  lomakkeen-virheet
         :footer   [napit/palvelinkutsu-nappi
                    "Tallenna turvallisuuspoikkeama"
                    #(tiedot/tallenna-turvallisuuspoikkeama @muokattu)
                    {:luokka       "nappi-ensisijainen"
                     :ikoni        (ikonit/envelope)
                     :kun-onnistuu #(do
                                     #_(tiedot/turvallisuuspoikkeaman-tallennus-onnistui % @muokattu)
                                     (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                     :disabled     (not @voi-tallentaa?)}]}
        [{:otsikko "Tyyppi" :tyyppi :valinta :nimi :tyyppi
          :valinnat [:turvallisuuspoikkeama :prosessipoikkeama :tyoturvallisuuspoikkeama]
          :valinta-nayta #(case %
                           :turvallisuuspoikkeama "Turvallisuuspoikkeama"
                           :prosessipoikkeama "Prosessipoikkeama"
                           :tyoturvallisuuspoikkeama "Työturvallisuuspoikkeama"
                           " - Valitse tyyppi -")}
         {:otsikko "Tapahtunut" :nimi :tapahtunut :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm}
         {:otsikko "Päättynyt" :nimi :paattynyt :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm}
         {:otsikko "Käsitelty" :nimi :kasitelty :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm}
         {:otsikko "Työntekijä" :nimi :tyontekijanammatti :leveys 1 :tyyppi :string}
         {:otsikko "Työtehtävä" :nimi :tyotehtava :leveys 1 :tyyppi :string}
         {:otsikko "Kuvaus" :nimi :kuvaus :leveys 1 :tyyppi :string}
         {:otsikko "Vammat" :nimi :vammat :leveys 1 :tyyppi :string}
         {:otsikko "Sairauspoissaolopäivät" :nimi :sairauspoissaolopaivat :leveys 1 :tyyppi :numero}
         {:otsikko "Sairaalavuorokaudet" :nimi :sairaalavuorokaudet :leveys 1 :tyyppi :numero}
         {:otsikko  "Tierekisteriosoite" :nimi :tr
          :tyyppi   :tierekisteriosoite
          :sijainti (r/wrap (:sijainti muokattu)
                            #(swap! muokattu assoc :sijainti %))}
         {:otsikko     "Kommentit" :nimi :kommentit
          :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                             :voi-liittaa      true
                                             :placeholder      "Kirjoita kommentti..."
                                             :uusi-kommentti   (r/wrap (:uusi-kommentti @muokattu)
                                                                       #(swap! muokattu assoc :uusi-kommentti %))}
                        (:kommentit @muokattu)]}
         {:otsikko "Vastaava henkilö" :nimi :vastaava
          :hae (comp :vastaavahenkilo :korjaavatoimenpide)
          :aseta #(assoc-in %1 [:korjaavatoimenpide :vastaavahenkilo] %2)
          :leveys 1 :tyyppi :string}
         {:otsikko "Kuvaus" :nimi :korjauksenkuvaus
          :hae (comp :kuvaus :korjaavatoimenpide)
          :aseta #(assoc-in %1 [:korjaavatoimenpide :kuvaus] %2)
          :leveys 1 :tyyppi :string}
         {:otsikko "Korjaus suoritettu" :nimi :korjauspvm :fmt pvm/pvm-aika
          :hae (comp :suoritettu :korjaavatoimenpide)
          :aseta #(assoc-in %1 [:korjaavatoimenpide :suoritettu] %2)
          :leveys 1 :tyyppi :pvm}]
        @muokattu]])))

(defn turvallisuuspoikkeamalistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama tiedot/+uusi-turvallisuuspoikkeama+)}
    (ikonit/plus-sign) " Lisää turvallisuuspoikkeama"]
   [grid/grid
    {:otsikko       "Turvallisuuspoikkeamat"
     :tyhja         (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-turvallisuuspoikkeama %)}
    [{:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :string :leveys "100%"}]
    @tiedot/haetut-turvallisuuspoikkeamat
    ]])

(defn turvallisuuspoikkeamat []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/lippu tiedot/taso-turvallisuuspoikkeamat)

    (fn []
      (if @tiedot/valittu-turvallisuuspoikkeama
        [turvallisuuspoikkeaman-tiedot]
        [turvallisuuspoikkeamalistaus]))))