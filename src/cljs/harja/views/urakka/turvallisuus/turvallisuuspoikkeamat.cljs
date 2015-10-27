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
            [harja.ui.kommentit :as kommentit]
            [cljs.core.async :refer [<!]]
            [harja.geo :as geo]
            [harja.views.kartta :as kartta])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn korjaavattoimenpiteet
  [toimenpiteet]
  #_[:div "HUHEUEH"]
  [grid/muokkaus-grid
   {:tyhja "Ei korjaavia toimenpiteitä"}
   [{:otsikko "Vastaava henkilö" :nimi :vastaavahenkilo :leveys "20%" :tyyppi :string}
    {:otsikko "Korjaus suoritettu" :nimi :suoritettu :fmt pvm/pvm :leveys "15%" :tyyppi :pvm}
    {:otsikko "Kuvaus" :nimi :kuvaus :leveys "65%" :tyyppi :text}]
   toimenpiteet])

(defn turvallisuuspoikkeaman-tiedot
  []

  (let [muokattu (reaction @tiedot/valittu-turvallisuuspoikkeama)
        lomakkeen-virheet (reaction @tiedot/valittu-turvallisuuspoikkeama {})
        voi-tallentaa? (reaction (and
                                   (= (count @lomakkeen-virheet) 0)
                                   (> (count @muokattu) (count tiedot/+uusi-turvallisuuspoikkeama+))))]

    (fn []
      (log "MUOKATTU: " (pr-str @muokattu))
      [:div
       [napit/takaisin "Takaisin luetteloon" #(reset! tiedot/valittu-turvallisuuspoikkeama nil)]

       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! #(reset! muokattu %)
         :virheet  lomakkeen-virheet
         :footer   [napit/palvelinkutsu-nappi
                    "Tallenna turvallisuuspoikkeama"
                    #(tiedot/tallenna-turvallisuuspoikkeama @muokattu)
                    {:luokka       "nappi-ensisijainen"
                     :ikoni        (ikonit/tallenna)
                     :kun-onnistuu #(do
                                     (tiedot/turvallisuuspoikkeaman-tallennus-onnistui %)
                                     (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                     :disabled     (not @voi-tallentaa?)}]}
        [{:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :boolean-group
          :vaihtoehdot [:turvallisuuspoikkeama :prosessipoikkeama :tyoturvallisuuspoikkeama]}

         (lomake/ryhma {:otsikko "Aika" :ulkoasu :rivi :leveys 3}
                       {:otsikko "Tapahtunut" :pakollinen? true :nimi :tapahtunut :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]]
                        :varoita [[:urakan-aikana-ja-hoitokaudella]]}
                       {:otsikko "Päättynyt" :pakollinen? true :nimi :paattynyt :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]
                                  [:pvm-kentan-jalkeen :tapahtunut "Ei voi päättyä ennen tapahtumisaikaa"]]}
                       {:otsikko "Käsitelty" :pakollinen? true :nimi :kasitelty :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]
                                  [:pvm-kentan-jalkeen :paattynyt "Ei voida käsitellä ennen päättymisaikaa"]]})
         
         {:otsikko "Työntekijä" :nimi :tyontekijanammatti :tyyppi :string :leveys-col 3}
         {:otsikko "Työtehtävä" :nimi :tyotehtava :tyyppi :string :leveys-col 3}
         {:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :text :koko [80 :auto] :leveys-col 4}
         {:otsikko "Vammat" :nimi :vammat :tyyppi :text :koko [80 :auto] :leveys-col 4}
         {:otsikko "Sairauspoissaolopäivät" :nimi :sairauspoissaolopaivat :leveys-col 1
          :tyyppi :positiivinen-numero :kokonaisluku? true}
         {:otsikko "Sairaalavuorokaudet" :nimi :sairaalavuorokaudet :leveys-col 1
          :tyyppi :positiivinen-numero :kokonaisluku? true}
         {:otsikko  "Tierekisteriosoite" :nimi :tr
          :tyyppi   :tierekisteriosoite
          :sijainti (r/wrap (:sijainti @muokattu)
                            #(swap! muokattu assoc :sijainti %))}
         {:otsikko     "Kommentit" :nimi :kommentit
          :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                             :voi-liittaa      true
                                             :placeholder      "Kirjoita kommentti..."
                                             :uusi-kommentti   (r/wrap (:uusi-kommentti @muokattu)
                                                                       #(swap! muokattu assoc :uusi-kommentti %))}
                        (:kommentit @muokattu)]}
         {:otsikko     "Korjaavat toimenpiteet" :nimi :korjaavattoimenpiteet :tyyppi :komponentti
          :komponentti [korjaavattoimenpiteet (r/wrap
                                                (into {} (map (juxt :id identity) (:korjaavattoimenpiteet @muokattu)))
                                                ;swap! muokattu assoc :korjaavattoimenpiteet
                                                (fn [uusi]
                                                  (swap!
                                                    muokattu
                                                    assoc
                                                    :korjaavattoimenpiteet
                                                    (vals
                                                      (filter
                                                        (fn [kartta]
                                                          (not
                                                            (and
                                                              (neg? (key kartta))
                                                              (:poistettu (val kartta)))))
                                                        uusi)))))]}]
        @muokattu]])))
  
(defn turvallisuuspoikkeamalistaus
  []
  (let [urakka @nav/valittu-urakka]
    [:div.sanktiot
     [urakka-valinnat/urakan-hoitokausi urakka]
     [:button.nappi-ensisijainen
      {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama tiedot/+uusi-turvallisuuspoikkeama+)}
      (ikonit/plus) " Lisää turvallisuuspoikkeama"]

     [grid/grid
      {:otsikko       "Turvallisuuspoikkeamat"
       :tyhja         (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(go
                         (reset! tiedot/valittu-turvallisuuspoikkeama
                                 (<! (tiedot/hae-turvallisuuspoikkeama (:id urakka)
                                                                       (:id %)))))}
      [{:otsikko "Tapahtunut" :nimi :tapahtunut :fmt pvm/pvm-aika :leveys "15%" :tyyppi :pvm}
       {:otsikko "Työntekija" :nimi :tyontekijanammatti :tyyppi :string :leveys "15%"}
       {:otsikko "Työtehtävä" :nimi :tyotehtava :tyyppi :string :leveys "15%"}
       {:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :string :leveys "45%"}
       {:otsikko "Poissa" :nimi :poissa :tyyppi :string :leveys "5%"
        :hae     (fn [rivi] (str (or (:sairaalavuorokaudet rivi) 0) "+" (or (:sairauspoissaolopaivat rivi) 0)))}
       {:otsikko "Korj." :nimi :korjaukset :tyyppi :string :leveys "5%"
        :hae (fn [rivi] (str (count (keep :suoritettu (:korjaavattoimenpiteet rivi))) "/" (count (:korjaavattoimenpiteet rivi))))}]
      @tiedot/haetut-turvallisuuspoikkeamat
      ]]))

(defn turvallisuuspoikkeamat []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-turvallisuuspoikkeamat)
    (komp/kuuntelija :turvallisuuspoikkeama-klikattu #(reset! tiedot/valittu-turvallisuuspoikkeama %2))
    (komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-turvallisuuspoikkeama))
    (fn []
      [:span
       [:h3 "Turvallisuuspoikkeamat"]
       [kartta/kartan-paikka]
       (if @tiedot/valittu-turvallisuuspoikkeama
         [turvallisuuspoikkeaman-tiedot]
         [turvallisuuspoikkeamalistaus])])))
