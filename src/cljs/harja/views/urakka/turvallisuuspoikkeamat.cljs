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
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.modal :as modal])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [harja.makrot :refer [defc fnc]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def +uusi-turvallisuuspoikkeama+ {:tila :avoin
                                   :vakavuusaste :lieva
                                   :vaylamuoto :tie
                                   :tyontekijanammatti :muu_tyontekija})

(defn rakenna-korjaavattoimenpiteet [turvallisuuspoikkeama-atom]
  (r/wrap
    (into {} (map (juxt :id identity) (:korjaavattoimenpiteet @turvallisuuspoikkeama-atom)))
    (fn [uusi]
      (swap!
        turvallisuuspoikkeama-atom
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

(defn- hakulomake [urakka valittu-kayttaja]
  [:div
   (when valittu-kayttaja
     [:div (str "Valittu käyttäjä: " (:etunimi valittu-kayttaja)
                " "
                (:sukunimi valittu-kayttaja))])
   [lomake/lomake {:otsikko "Käyttäjän tiedot"
                   :muokkaa! (fn [uusi-data]
                               (reset! tiedot/kayttajahakulomake-data uusi-data))
                   :footer [napit/palvelinkutsu-nappi
                            "Hae"
                            #(tiedot/hae-kayttajat (merge {:urakka-id (:id urakka)}
                                                          @tiedot/kayttajahakulomake-data))
                            {:luokka "nappi-ensisijainen"
                             :virheviesti "Käyttäjien haku epäonnistui."
                             :kun-virhe (fn [_]
                                          (reset! tiedot/kayttajahakutulokset-data []))
                             :kun-onnistuu (fn [vastaus]
                                             (log "[TURPO] Käyttäjät haettu onnistuneesti: " (pr-str vastaus))
                                             (reset! tiedot/kayttajahakutulokset-data vastaus))}]}
    [{:otsikko "Etunimi"
      :nimi :etunimi
      :pituus-max 512
      :tyyppi :string}
     {:otsikko "Sukunimi"
      :nimi :sukunimi
      :pituus-max 512
      :tyyppi :string}
     {:otsikko "Käyttäjänimi"
      :nimi :kayttajanimi
      :pituus-max 512
      :tyyppi :string}]
    @tiedot/kayttajahakulomake-data]])

(defn- hakutulokset [korjaava-toimenpide toimenpiteet-atom]
  [grid/grid
   {:otsikko "Löytyneet käyttäjät"
    :tyhja (if (nil? @tiedot/kayttajahakutulokset-data)
             [ajax-loader "Haetaan käyttäjiä..."]
             "Käyttäjiä ei löytynyt")}
   [{:otsikko "Käyttäjätunnus"
     :nimi :kayttajanimi
     :tyyppi :string
     :muokattava? (constantly false)
     :leveys 10}
    {:otsikko "Etunimi"
     :nimi :etunimi
     :tyyppi :string
     :muokattava? (constantly false)
     :leveys 10}
    {:otsikko "Sukunimi"
     :nimi :sukunimi
     :tyyppi :string
     :muokattava? (constantly false)
     :leveys 10}
    {:otsikko "Valinta"
     :nimi :valitse
     :tyyppi :komponentti
     :leveys 5
     :komponentti (fn [rivi]
                    [:button.nappi-ensisijainen {:on-click (fn [e]
                                                             (let [korjaava-toimenpide-id (.preventDefault e)]
                                                               (swap! toimenpiteet-atom
                                                                      assoc
                                                                      korjaava-toimenpide-id
                                                                      (assoc korjaava-toimenpide :vastuuhenkilo rivi)))
                                                             (modal/piilota!))}
                     "Valitse"])}]
   @tiedot/kayttajahakutulokset-data])

(defn kayttajahaku-modal-sisalto [korjaava-toimenpide toimenpiteet-atom urakka]
  [:div
   [hakulomake urakka (:vastuuhenkilo korjaava-toimenpide)]
   [hakutulokset korjaava-toimenpide toimenpiteet-atom]])

(defn avaa-kayttajahaku-modal [korjaava-toimenpide toimenpiteet-atom urakka]
  (reset! tiedot/kayttajahakutulokset-data [])
  (modal/nayta!
    {:otsikko "Käyttäjähaku"
     :luokka "turvallisuuspoikkeama-kayttajahaku"
     :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                      (.preventDefault e)
                                                      (modal/piilota!))}
              "Sulje"]}
    [kayttajahaku-modal-sisalto korjaava-toimenpide toimenpiteet-atom urakka]))

(defn korjaavattoimenpiteet
  [toimenpiteet]
  [grid/muokkaus-grid
   {:tyhja "Ei korjaavia toimenpiteitä"}
   [{:otsikko "Otsikko"
     :nimi :otsikko
     :leveys 20
     :tyyppi :string
     :pituus-max 2048}
    {:otsikko "Tila"
     :nimi :tila
     :pakollinen? true
     :validoi [[:ei-tyhja "Valitse tila"]]
     :tyyppi :valinta
     :valinta-nayta #(or ({:avoin "Avoin"
                           :siirretty "Siirretty"
                           :toteutettu "Toteutettu"}
                           %)
                         "- valitse -")
     :valinnat [:avoin :siirretty :toteutettu]
     :leveys 15}
    {:otsikko "Laatija"
     :nimi :laatija
     :leveys 20
     :tyyppi :string
     :muokattava? (constantly false)
     :fmt (fn [_] (str (:etunimi @istunto/kayttaja)
                       " " (:sukunimi @istunto/kayttaja)))}
    {:otsikko "Vastuuhenkilö"
     :nimi :vastuuhenkilo
     :leveys 25
     :fmt #(str (:etunimi %) " " (:sukunimi %))
     :tyyppi :komponentti
     :komponentti (fn [rivi] [:button.nappi-ensisijainen.nappi-grid
                              {:on-click #(avaa-kayttajahaku-modal
                                           rivi
                                           toimenpiteet
                                           @nav/valittu-urakka)}
                              (if (:vastuuhenkilo rivi)
                                (str "Vaihda " (get-in rivi [:vastuuhenkilo :etunimi])
                                     " " (get-in rivi [:vastuuhenkilo :sukunimi]))
                                "Hae käyttäjä")])}
    {:otsikko "Toteuttaja"
     :nimi :toteuttaja
     :leveys 20
     :tyyppi :string
     :pituus-max 1024}
    {:otsikko "Korjaus suoritettu" :nimi :suoritettu :fmt pvm/pvm :leveys 15 :tyyppi :pvm}
    {:otsikko "Kuvaus" :nimi :kuvaus :leveys 25 :tyyppi :text :koko [80 :auto]}]
   toimenpiteet])

(defn- voi-tallentaa? [tp]
  (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id @nav/valittu-urakka))
       (if-not (:id tp)
         (lomake/voi-tallentaa-ja-muokattu? tp)
         (lomake/voi-tallentaa? tp))))

(defn turvallisuuspoikkeaman-tiedot []
  (let [turvallisuuspoikkeama (reaction @tiedot/valittu-turvallisuuspoikkeama)]
    (fnc []
      (let [henkilovahinko-valittu? (and (set? (:vahinkoluokittelu @turvallisuuspoikkeama))
                                         ((:vahinkoluokittelu @turvallisuuspoikkeama) :henkilovahinko))
            vaaralliset-aineet-disablointi-fn (fn [valitut vaihtoehto]
                                                (and
                                                  (= vaihtoehto :vaarallisten-aineiden-vuoto)
                                                  (not (valitut :vaarallisten-aineiden-kuljetus))))
            henkilovahinkojen-disablointi-fn (fn [valitut vaihtoehto]
                                               (or (and
                                                     (not= vaihtoehto :ei_tietoa)
                                                     (valitut :ei_tietoa))
                                                   (and (= vaihtoehto :ei_tietoa)
                                                        (not (empty? valitut))
                                                        (not (valitut :ei_tietoa)))))]
        (log "[TURPO] Render lomake: " (pr-str @turvallisuuspoikkeama))
        [:div
         [napit/takaisin "Takaisin luetteloon" #(reset! tiedot/valittu-turvallisuuspoikkeama nil)]
         (when (false? (:lahetysonnistunut @turvallisuuspoikkeama))
           (lomake/yleinen-varoitus (str "Turvallisuuspoikkeaman lähettäminen TURI:n epäonnistui "
                                         (pvm/pvm-aika (:lahetetty @turvallisuuspoikkeama)))))
         [lomake/lomake
          {:otsikko (if (:id @turvallisuuspoikkeama) "Muokkaa turvallisuuspoikkeamaa" "Luo uusi turvallisuuspoikkeama")
           :muokkaa! #(let [tarkistettu-lomakedata (if (= (:vaaralliset-aineet %) #{:vaarallisten-aineiden-vuoto})
                                                     (assoc % :vaaralliset-aineet #{})
                                                     %)]
                       (reset! turvallisuuspoikkeama tarkistettu-lomakedata))
           :voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id @nav/valittu-urakka))
           :footer [napit/palvelinkutsu-nappi
                    "Tallenna turvallisuuspoikkeama"
                    #(tiedot/tallenna-turvallisuuspoikkeama @turvallisuuspoikkeama)
                    {:luokka "nappi-ensisijainen"
                     :ikoni (ikonit/tallenna)
                     :kun-onnistuu #(do
                                     (tiedot/turvallisuuspoikkeaman-tallennus-onnistui %)
                                     (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                     :virheviesti "Turvallisuuspoikkeaman tallennus epäonnistui."
                     :disabled (not (voi-tallentaa? @turvallisuuspoikkeama))}]}
          [{:otsikko "Tapahtuman otsikko"
            :nimi :otsikko
            :tyyppi :string
            :pituus-max 1024
            :pakollinen? true
            :validoi [[:ei-tyhja "Valitse tila"]]
            :palstoja 1}
           {:otsikko "Tapahtunut" :pakollinen? true
            :nimi :tapahtunut :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
            :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]]
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
           (lomake/ryhma {:rivi? true}
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
           {:otsikko "Tierekisteriosoite"
            :nimi :tr
            :pakollinen? true
            :tyyppi :tierekisteriosoite
            :sijainti (r/wrap (:sijainti @turvallisuuspoikkeama)
                              #(swap! turvallisuuspoikkeama assoc :sijainti %))}
           {:otsikko "Paikan kuvaus"
            :nimi :paikan-kuvaus
            :tyyppi :string
            :pituus-max 2048
            :palstoja 1}
           {:uusi-rivi? true
            :otsikko "Tila"
            :nimi :tila
            :pakollinen? true
            :validoi [[:ei-tyhja "Valitse tila"]
                      [:ei-avoimia-korjaavia-toimenpiteitä
                       "Voidaan sulkea vasta kun kaikki korjaavat toimenpiteet on toteutettu"]]
            :tyyppi :valinta
            :valinta-nayta #(or (turpodomain/kuvaa-turpon-tila %)
                                "- valitse -")
            :valinnat [:avoin :kasitelty :taydennetty :suljettu]
            :palstoja 1}
           {:otsikko "Tapahtuman kuvaus"
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
           {:otsikko "Vaaralliset aineet" :nimi :vaaralliset-aineet :tyyppi :checkbox-group
            :vaihtoehto-nayta turpodomain/turpo-vaaralliset-aineet
            :disabloi vaaralliset-aineet-disablointi-fn
            :nayta-rivina? true
            :vaihtoehdot #{:vaarallisten-aineiden-kuljetus :vaarallisten-aineiden-vuoto}}
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
               (when (= :muu_tyontekija (:tyontekijanammatti @turvallisuuspoikkeama))
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
                :vaihtoehdot turpodomain/vahingoittunut-ruumiinosa-avaimet-jarjestyksessa
                :vaihtoehto-nayta turpodomain/vahingoittunut-ruumiinosa}))
           {:otsikko "Kommentit" :nimi :kommentit
            :tyyppi :komponentti
            :palstoja 2
            :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                               :voi-liittaa true
                                               :placeholder "Kirjoita kommentti..."
                                               :uusi-kommentti (r/wrap (:uusi-kommentti @turvallisuuspoikkeama)
                                                                       #(swap! turvallisuuspoikkeama assoc :uusi-kommentti %))}
                          (:kommentit @turvallisuuspoikkeama)]}
           (lomake/ryhma {:otsikko "Poikkeaman käsittely"}
                         {:otsikko "Poikkeama kirjattu" :nimi :luotu :fmt pvm/pvm-aika-opt :tyyppi :string
                          :muokattava? (constantly false)
                          :uusi-rivi? true
                          }
                         {:otsikko "Korjaavat toimenpiteet" :nimi :korjaavattoimenpiteet :tyyppi :komponentti
                          :palstoja 2
                          :uusi-rivi? true
                          :komponentti [korjaavattoimenpiteet (rakenna-korjaavattoimenpiteet turvallisuuspoikkeama)]}
                         {:otsikko "Ilmoitukset lähetetty" :nimi :ilmoituksetlahetetty :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
                          :validoi [[:pvm-kentan-jalkeen :tapahtunut "Ei voi päättyä ennen tapahtumisaikaa"]]}
                         {:otsikko "Loppuunkäsitelty" :nimi :kasitelty :fmt #(if %
                                                                              (pvm/pvm-aika-opt %)
                                                                              "Ei")
                          :tyyppi :pvm-aika
                          :muokattava? (constantly false)})]
          @turvallisuuspoikkeama]]))))

(defn valitse-turvallisuuspoikkeama [urakka-id turvallisuuspoikkeama-id]
  (go
    (reset! tiedot/valittu-turvallisuuspoikkeama
            (<! (tiedot/hae-turvallisuuspoikkeama urakka-id turvallisuuspoikkeama-id)))))

(defn turvallisuuspoikkeamalistaus
  []
  (let [urakka @nav/valittu-urakka]
    [:div.sanktiot
     [urakka-valinnat/urakan-hoitokausi urakka]
     [napit/uusi "Lisää turvallisuuspoikkeama" #(reset! tiedot/valittu-turvallisuuspoikkeama +uusi-turvallisuuspoikkeama+)
      {:disabled (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id urakka)))}]

     [grid/grid
      {:otsikko "Turvallisuuspoikkeamat"
       :tyhja (if @tiedot/haetut-turvallisuuspoikkeamat "Ei löytyneitä tietoja" [ajax-loader "Haetaan turvallisuuspoikkeamia"])
       :rivi-klikattu #(valitse-turvallisuuspoikkeama (:id urakka) (:id %))}
      [{:otsikko "Ta\u00ADpah\u00ADtu\u00ADnut" :nimi :tapahtunut :fmt pvm/pvm-aika :leveys 15 :tyyppi :pvm}
       {:otsikko "Ty\u00ADön\u00ADte\u00ADki\u00ADjä" :nimi :tyontekijanammatti :leveys 15
        :hae turpodomain/kuvaile-tyontekijan-ammatti}
       {:otsikko "Ku\u00ADvaus" :nimi :kuvaus :tyyppi :string :leveys 45}
       {:otsikko "Tila" :nimi :tila :tyyppi :string :leveys 8 :fmt turpodomain/kuvaa-turpon-tila
        :validoi [[:ei-tyhja "Valitse tila"]]}
       {:otsikko "Pois\u00ADsa" :nimi :poissa :tyyppi :string :leveys 5
        :hae (fn [rivi] (str (or (:sairaalavuorokaudet rivi) 0) "+" (or (:sairauspoissaolopaivat rivi) 0)))}
       {:otsikko "Korj." :nimi :korjaukset :tyyppi :string :leveys 5
        :hae (fn [rivi] (str (count (keep :suoritettu (:korjaavattoimenpiteet rivi))) "/" (count (:korjaavattoimenpiteet rivi))))}]
      (sort-by :tapahtunut pvm/jalkeen? @tiedot/haetut-turvallisuuspoikkeamat)]]))

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
