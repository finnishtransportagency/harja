(ns harja.views.urakka.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.turvallisuuspoikkeamat :as tiedot]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
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
            [harja.views.kartta :as kartta])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [harja.makrot :refer [defc fnc]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn rakenna-korjaavattoimenpiteet [turvallisuuspoikkeama]
  (r/wrap
    (into {} (map (juxt :id identity) (:korjaavattoimenpiteet turvallisuuspoikkeama)))
    ;swap! muokattu assoc :korjaavattoimenpiteet
    (fn [uusi]
      (swap!
        turvallisuuspoikkeama
        assoc
        :korjaavattoimenpiteet
        (vals
          (filter
            (fn [kartta]
              (not
                (and
                  (neg? (key kartta))
                  (:poistettu (val kartta)))))
            uusi))))))

(defn korjaavattoimenpiteet
  [toimenpiteet]
  [grid/muokkaus-grid
   {:tyhja "Ei korjaavia toimenpiteitä"}
   [{:otsikko "Vastaava henkilö" :nimi :vastaavahenkilo :leveys "20%" :tyyppi :string}
    {:otsikko "Korjaus suoritettu" :nimi :suoritettu :fmt pvm/pvm :leveys "15%" :tyyppi :pvm}
    {:otsikko "Kuvaus" :nimi :kuvaus :leveys "65%" :tyyppi :text :koko [80 :auto]}]
   toimenpiteet])

(defn turvallisuuspoikkeaman-tiedot []
  (let [muokattu (reaction @tiedot/valittu-turvallisuuspoikkeama)]
    (fnc []
         (let [henkilovahinko-valittu? (and (set? (:vahinkoluokittelu @muokattu))
                                            ((:vahinkoluokittelu @muokattu) :henkilovahinko))
               henkilovahinkojen-disablointi-fn (fn [valitut vaihtoehto]
                                                  (or (and (valitut :ei_tietoa)
                                                           (not= vaihtoehto :ei_tietoa))
                                                      (and (not (empty? valitut))
                                                           (not (valitut :ei_tietoa))
                                                           (= vaihtoehto :ei_tietoa))))]
           [:div
            [napit/takaisin "Takaisin luetteloon" #(reset! tiedot/valittu-turvallisuuspoikkeama nil)]

            [lomake/lomake
             {:otsikko (if (:id @muokattu) "Luo uusi turvallisuuspoikkeama" "Muokkaa turvallisuuspoikkeamaa")
              :muokkaa! #(do (log "TURPO: " (pr-str %)) (reset! muokattu %))
              :footer [napit/palvelinkutsu-nappi
                       "Tallenna turvallisuuspoikkeama"
                       #(tiedot/tallenna-turvallisuuspoikkeama @muokattu)
                       {:luokka "nappi-ensisijainen"
                        :ikoni (ikonit/tallenna)
                        :kun-onnistuu #(do
                                        (tiedot/turvallisuuspoikkeaman-tallennus-onnistui %)
                                        (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                        :disabled (not (lomake/voi-tallentaa? @muokattu))}]}
             [(lomake/ryhma {:rivi? true}
                            {:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :checkbox-group
                             :pakollinen? true
                             :vaihtoehto-nayta turpodomain/turpo-tyypit
                             :validoi [#(when (empty? %) "Anna turvallisuuspoikkeaman tyyppi")]
                             :vaihtoehdot (keys turpodomain/turpo-tyypit)}
                            {:otsikko "Vahinkoluokittelu" :nimi :vahinkoluokittelu :tyyppi :checkbox-group
                             :pakollinen? true
                             :vaihtoehto-nayta #(turpodomain/vahinkoluokittelu-tyypit %)
                             :validoi [#(when (empty? %) "Anna turvallisuuspoikkeaman vahinkoluokittelu")]
                             :vaihtoehdot (keys turpodomain/vahinkoluokittelu-tyypit)}
                            {:otsikko "Vakavuusaste" :nimi :vakavuusaste :tyyppi :radio-group
                             :pakollinen? true
                             :vaihtoehto-nayta #(turpodomain/turpo-vakavuusasteet %)
                             :validoi [#(when (nil? %) "Anna turvallisuuspoikkeaman vakavuusaste")]
                             :vaihtoehdot (keys turpodomain/turpo-vakavuusasteet)})
              (lomake/ryhma {:rivi? true
                             :otsikko "Tapahtuma-aika"}
                            {:otsikko "Alkanut" :pakollinen? true :nimi :tapahtunut :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                             :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]]
                             :varoita [[:urakan-aikana-ja-hoitokaudella]]}
                            {:otsikko "Päättynyt" :pakollinen? true :nimi :paattynyt :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                             :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]
                                       [:pvm-kentan-jalkeen :tapahtunut "Ei voi päättyä ennen tapahtumisaikaa"]]})
              {:rivi? true
               :otsikko "Tierekisteriosoite"
               :nimi :tr
               :tyyppi :tierekisteriosoite
               :uusi-rivi? true
               :sijainti (r/wrap (:sijainti @muokattu)
                                 #(swap! muokattu assoc :sijainti %))}

              {:uusi-rivi? true
               :otsikko "Kuvaus"
               :nimi :kuvaus
               :tyyppi :text
               :koko [80 :auto]
               :palstoja 1
               :pakollinen? true
               :validoi [[:ei-tyhja "Anna kuvaus"]]}
              {:otsikko "Aiheutuneet seuraukset"
               :nimi :seuraukset
               :tyyppi :text
               :koko [80 :auto]
               :palstoja 1}
              {:otsikko "Toteuttaja"
               :nimi :toteuttaja
               :tyyppi :string
               :palstoja 1}
              {:otsikko "Tilaaja"
               :nimi :tilaaja
               :tyyppi :string
               :palstoja 1}
              (lomake/ryhma {:otsikko "Turvallisuuskoordinaattori"
                             :uusi-rivi? true}
                            {:otsikko "Etunimi"
                             :nimi :turvallisuuskoordinaattorietunimi
                             :tyyppi :string
                             :palstoja 1}
                            {:otsikko "Sukunimi"
                             :nimi :turvallisuuskoordinaattorisukunimi
                             :tyyppi :string
                             :palstoja 1})
              (lomake/ryhma {:otsikko "Laatija"
                             :uusi-rivi? true}
                            {:otsikko "Etunimi"
                             :nimi :laatijaetunimi
                             :tyyppi :string
                             :palstoja 1}
                            {:otsikko "Sukunimi"
                             :nimi :laatijasukunimi
                             :tyyppi :string
                             :palstoja 1})
              (when henkilovahinko-valittu?
                (lomake/ryhma
                  "Henkilövahingon tiedot"
                  {:otsikko "Työntekijän ammatti"
                   :nimi :tyontekijanammatti
                   :tyyppi :valinta
                   :pakollinen? true
                   :valinnat (sort (keys turpodomain/turpo-tyontekijan-ammatit))
                   :valinta-nayta #(or (turpodomain/turpo-tyontekijan-ammatit %) "- valitse -")
                   :uusi-rivi? true}
                  {:otsikko "Työtehtävä" :nimi :tyotehtava :tyyppi :string :palstoja 1}
                  (when (= :muu_tyontekija (:tyontekijanammatti @muokattu))
                    {:otsikko "Muu ammatti" :nimi :tyontekijanammattimuu :tyyppi :string :palstoja 1})
                  (lomake/ryhma {:rivi? true}
                                {:otsikko "Sairaalavuorokaudet" :nimi :sairaalavuorokaudet :palstoja 1
                                 :tyyppi :positiivinen-numero :kokonaisluku? true}
                                {:otsikko "Sairauspoissaolopäivät" :nimi :sairauspoissaolopaivat :palstoja 1
                                 :tyyppi :positiivinen-numero :kokonaisluku? true}
                                {:nimi :sairauspoissaolojatkuu
                                 :palstoja 1
                                 :tyyppi :checkbox
                                 :teksti "Sairauspoissaolo jatkuu"})
                  {:otsikko "Vammat"
                   :nimi :vammat
                   :uusi-rivi? true
                   :palstoja 1
                   :tyyppi :checkbox-group
                   :disabloi henkilovahinkojen-disablointi-fn
                   :vaihtoehdot turpodomain/vammat-avaimet-jarjestyksessa
                   :vaihtoehto-nayta turpodomain/vammat}
                  {:otsikko "Vahingoittuneet ruumiinosat"
                   :nimi :vahingoittuneetruumiinosat
                   :palstoja 1
                   :tyyppi :checkbox-group
                   :disabloi henkilovahinkojen-disablointi-fn
                   :vaihtoehdot turpodomain/vahingoizttunut-ruumiinosa-avaimet-jarjestyksessa
                   :vaihtoehto-nayta turpodomain/vahingoittunut-ruumiinosa}))
              {:otsikko "Kommentit" :nimi :kommentit
               :tyyppi :komponentti
               :palstoja 2
               :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                                  :voi-liittaa true
                                                  :placeholder "Kirjoita kommentti..."
                                                  :uusi-kommentti (r/wrap (:uusi-kommentti @muokattu)
                                                                          #(swap! muokattu assoc :uusi-kommentti %))}
                             (:kommentit @muokattu)]}
              (lomake/ryhma {:otsikko "Poikkeaman käsittely"}
                            {:otsikko "Poikkeama kirjattu" :nimi :luotu :fmt pvm/pvm-aika-opt :tyyppi :string
                             :muokattava? (constantly false)
                             :uusi-rivi? true
                             }
                            {:otsikko "Korjaavat toimenpiteet" :nimi :korjaavattoimenpiteet :tyyppi :komponentti
                             :palstoja 2
                             :uusi-rivi? true
                             :komponentti [korjaavattoimenpiteet (rakenna-korjaavattoimenpiteet @muokattu)]}
                            {:otsikko "Ilmoitukset lähetetty" :nimi :ilmoituksetlahetetty :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                             :validoi [[:pvm-kentan-jalkeen :tapahtunut "Ei voi päättyä ennen tapahtumisaikaa"]]}
                            {:otsikko "Loppuunkäsitelty" :nimi :kasitelty :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                             :validoi [[:pvm-kentan-jalkeen :paattynyt "Ei voida käsitellä ennen päättymisaikaa"]]})]
             @muokattu]]))))

(defn valitse-turvallisuuspoikkeama [urakka-id turvallisuuspoikkeama-id]
  (go
    (reset! tiedot/valittu-turvallisuuspoikkeama
            (<! (tiedot/hae-turvallisuuspoikkeama urakka-id turvallisuuspoikkeama-id)))))

(defn turvallisuuspoikkeamalistaus
  []
  (let [urakka @nav/valittu-urakka]
    [:div.sanktiot
     [urakka-valinnat/urakan-hoitokausi urakka]
     [:button.nappi-ensisijainen
      {:on-click #(reset! tiedot/valittu-turvallisuuspoikkeama tiedot/+uusi-turvallisuuspoikkeama+)}
      (ikonit/plus) " Lisää turvallisuuspoikkeama"]

     [grid/grid
      {:otsikko "Turvallisuuspoikkeamat"
       :tyhja (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan turvallisuuspoikkeamia"])
       :rivi-klikattu #(valitse-turvallisuuspoikkeama (:id urakka) (:id %))}
      [{:otsikko "Ta\u00ADpah\u00ADtu\u00ADnut" :nimi :tapahtunut :fmt pvm/pvm-aika :leveys "15%" :tyyppi :pvm}
       {:otsikko "Ty\u00ADön\u00ADte\u00ADki\u00ADjä" :nimi :tyontekijanammatti :leveys "15%"
        :hae turpodomain/kuvaile-tyontekijan-ammatti}
       {:otsikko "Ty\u00ADöteh\u00ADtä\u00ADvä" :nimi :tyotehtava :tyyppi :string :leveys "15%"}
       {:otsikko "Ku\u00ADvaus" :nimi :kuvaus :tyyppi :string :leveys "45%"}
       {:otsikko "Pois\u00ADsa" :nimi :poissa :tyyppi :string :leveys "5%"
        :hae (fn [rivi] (str (or (:sairaalavuorokaudet rivi) 0) "+" (or (:sairauspoissaolopaivat rivi) 0)))}
       {:otsikko "Korj." :nimi :korjaukset :tyyppi :string :leveys "5%"
        :hae (fn [rivi] (str (count (keep :suoritettu (:korjaavattoimenpiteet rivi))) "/" (count (:korjaavattoimenpiteet rivi))))}]
      @tiedot/haetut-turvallisuuspoikkeamat]]))

(defn turvallisuuspoikkeamat []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-turvallisuuspoikkeamat)
    (komp/kuuntelija :turvallisuuspoikkeama-klikattu #(valitse-turvallisuuspoikkeama (:id @nav/valittu-urakka) (:id %2)))
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(do
                        (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (komp/ulos (kartta/kuuntele-valittua! tiedot/valittu-turvallisuuspoikkeama))
    (fn []
      [:span
       [:h3 "Turvallisuuspoikkeamat"]
       [kartta/kartan-paikka]
       (if @tiedot/valittu-turvallisuuspoikkeama
         [turvallisuuspoikkeaman-tiedot]
         [turvallisuuspoikkeamalistaus])])))
