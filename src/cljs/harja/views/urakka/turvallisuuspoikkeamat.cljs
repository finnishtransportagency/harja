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
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [harja.makrot :refer [defc fnc]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

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
                    [:button.nappi-ensisijainen {:on-click
                                                 (fn [e]
                                                   (let [korjaava-toimenpide-id (:id korjaava-toimenpide)]
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
  [toimenpiteet turvallisuuspoikkeama toimenpiteet-virheet-atom muokkaa-lomaketta-fn]
  [grid/muokkaus-grid
   {:tyhja "Ei korjaavia toimenpiteitä"
    :muutos #(do (reset! toimenpiteet-virheet-atom (grid/hae-virheet %))
                 (muokkaa-lomaketta-fn @turvallisuuspoikkeama))}
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
     :hae (fn [rivi]
            (if (neg? (:id rivi))
              ;; Uusi rivi, laatijaksi tullaan liittämään nykyinen käyttäjä
              (str (:etunimi @istunto/kayttaja) " " (:sukunimi @istunto/kayttaja))
              (str (:laatija-etunimi rivi)
                   " " (:laatija-sukunimi rivi))))}
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

(defn- voi-tallentaa? [tp toimenpiteet-virheet]
  (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id @nav/valittu-urakka))
       (empty? toimenpiteet-virheet)
       (lomake/voi-tallentaa-ja-muokattu? tp)))

(defn turvallisuuspoikkeaman-tiedot []
  (let [turvallisuuspoikkeama (reaction-writable @tiedot/valittu-turvallisuuspoikkeama)
        toimenpiteet-virheet (atom nil)]
    (fnc []
         (let [henkilovahinko-valittu? (and (set? (:vahinkoluokittelu @turvallisuuspoikkeama))
                                            ((:vahinkoluokittelu @turvallisuuspoikkeama) :henkilovahinko))
               vaaralliset-aineet-disablointi-fn (fn [valitut vaihtoehto]
                                                   (and
                                                     (= vaihtoehto :vaarallisten-aineiden-vuoto)
                                                     (not (valitut :vaarallisten-aineiden-kuljetus))))]
           [:div
            [napit/takaisin "Takaisin luetteloon" #(reset! tiedot/valittu-turvallisuuspoikkeama nil)]
            (when (false? (:lahetysonnistunut @turvallisuuspoikkeama))
              (lomake/yleinen-varoitus (str "Turvallisuuspoikkeaman lähettäminen TURI:iin epäonnistui "
                                            (pvm/pvm-aika (:lahetetty @turvallisuuspoikkeama)))))
            [lomake/lomake
             {:otsikko (if (:id @turvallisuuspoikkeama) "Muokkaa turvallisuuspoikkeamaa" "Luo uusi turvallisuuspoikkeama")
              :muokkaa! #(let [tarkistettu-lomakedata (if (= (:vaaralliset-aineet %) #{:vaarallisten-aineiden-vuoto})
                                                        (assoc % :vaaralliset-aineet #{})
                                                        %)]
                           (reset! turvallisuuspoikkeama tarkistettu-lomakedata))
              :voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id @nav/valittu-urakka))
              :footer-fn (fn [tp]
                           [:div
                            [napit/palvelinkutsu-nappi
                             "Tallenna turvallisuuspoikkeama"
                             #(tiedot/tallenna-turvallisuuspoikkeama (lomake/ilman-lomaketietoja tp))
                             {:luokka "nappi-ensisijainen"
                              :ikoni (ikonit/tallenna)
                              :kun-onnistuu #(do
                                               (tiedot/turvallisuuspoikkeaman-tallennus-onnistui %)
                                               (reset! tiedot/valittu-turvallisuuspoikkeama nil))
                              :virheviesti "Turvallisuuspoikkeaman tallennus epäonnistui."
                              :disabled (not (voi-tallentaa? tp @toimenpiteet-virheet))}]
                            [:div [lomake/nayta-puuttuvat-pakolliset-kentat tp]]
                            [yleiset/vihje "Turvallisuuspoikkeama lähetetään automaattisesti TURI:iin aina tallentaessa"]])}
             [{:otsikko "Tapahtuman otsikko"
               :nimi :otsikko
               :tyyppi :string
               :pituus-max 1024
               :pakollinen? true
               :validoi [[:ei-tyhja "Valitse tila"]]
               :palstoja 1}
              {:otsikko "Tapahtunut" :pakollinen? true
               :nimi :tapahtunut :fmt pvm/pvm-aika-opt :tyyppi :pvm-aika
               :validoi [[:ei-tyhja "Aseta päivämäärä ja aika"]
                         [:ei-tulevaisuudessa "Päivämäärä ei voi olla tulevaisuudessa"]]
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
                             :vaihtoehdot (keys turpodomain/turpo-vakavuusasteet)
                             :vihje (str "Vakavaksi työtapaturmaksi katsotaan tilanne, jonka seurauksena on kuolema, yli 30 päivän poissaolo työstä tai vaikealaatuinen vamma. \n"
                                         "Vakavaksi vaaratilanteeksi katsotaan tilanne, jonka seurauksena olisi voinut aiheutua vakava työtapaturma. \n"
                                         "Vakavaksi ympäristövahingoksi katsotaan tilanne, jonka seurauksena paikalle joudutaan pyytämään pelastusviranomainen.")})
              {:otsikko "Tierekisteriosoite"
               :nimi :tr
               :pakollinen? true
               :tyyppi :tierekisteriosoite
               :ala-nayta-virhetta-komponentissa? true
               :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
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
                          "Voidaan merkitä suljetuksi tai käsitellyksi
                          vasta kun kaikki korjaavat toimenpiteet on toteutettu"]]
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
               :pituus-max 2000
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
              {:otsikko "Laatija"
               :nimi :laatija
               :leveys 20
               :tyyppi :string
               :muokattava? (constantly false)
               :fmt (fn [_] (if (:uusi? (meta @turvallisuuspoikkeama))
                              ;; Laatijaksi tullaan liittämään nykyinen käyttäjä
                              (str (:etunimi @istunto/kayttaja) " " (:sukunimi @istunto/kayttaja))
                              (str (:laatija-etunimi @turvallisuuspoikkeama)
                                   " " (:laatija-sukunimi @turvallisuuspoikkeama))))}
              {:otsikko "Vaaralliset aineet" :nimi :vaaralliset-aineet :tyyppi :checkbox-group
               :vaihtoehto-nayta turpodomain/turpo-vaaralliset-aineet
               :disabloi vaaralliset-aineet-disablointi-fn
               :nayta-rivina? true
               :vaihtoehdot #{:vaarallisten-aineiden-kuljetus :vaarallisten-aineiden-vuoto}}
              {:otsikko "Liitteet" :nimi :liitteet
               :palstoja 2
               :tyyppi :komponentti
               :komponentti
               (fn [_]
                 [liitteet/liitteet (:id @nav/valittu-urakka) (:liitteet @turvallisuuspoikkeama)
                  {:uusi-liite-atom (r/wrap (:uusi-liite @turvallisuuspoikkeama)
                                            #(swap! turvallisuuspoikkeama assoc :uusi-liite %))
                   :uusi-liite-teksti "Lisää liite turvallisuuspoikkeamaan"}])}
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
                                 :tyyppi :positiivinen-numero :kokonaisluku? true
                                 :validoi [[:rajattu-numero nil 0 10000 "Anna arvo väliltä 0 - 10 000"]]}
                                {:otsikko "Sairauspoissaolopäivät" :nimi :sairauspoissaolopaivat :palstoja 1
                                 :tyyppi :positiivinen-numero :kokonaisluku? true
                                 :validoi [[:rajattu-numero nil 0 10000 "Anna arvo väliltä 0 - 10 000"]]}
                                {:nimi :sairauspoissaolojatkuu
                                 :palstoja 1
                                 :tyyppi :checkbox
                                 :teksti "Sairauspoissaolo jatkuu"})
                  {:otsikko "Vamma"
                   :nimi :vammat
                   :uusi-rivi? true
                   :palstoja 1
                   :tyyppi :valinta
                   :valinnat turpodomain/vammat-avaimet-jarjestyksessa
                   :valinta-nayta #(or (turpodomain/vammat %) "- valitse -")}
                  {:otsikko "Vahingoittunut ruumiinosat"
                   :nimi :vahingoittuneetruumiinosat
                   :palstoja 1
                   :tyyppi :valinta
                   :valinnat turpodomain/vahingoittunut-ruumiinosa-avaimet-jarjestyksessa
                   :valinta-nayta #(or (turpodomain/vahingoittunut-ruumiinosa %) "- valitse -")}))
              {:otsikko "Kommentit" :nimi :kommentit
               :tyyppi :komponentti
               :palstoja 2
               :komponentti (fn [_]
                              [kommentit/kommentit {:voi-kommentoida? true
                                                    :voi-liittaa true
                                                    :placeholder "Kirjoita kommentti..."
                                                    :uusi-kommentti (r/wrap (:uusi-kommentti @turvallisuuspoikkeama)
                                                                            #(swap! turvallisuuspoikkeama assoc :uusi-kommentti %))}
                               (:kommentit @turvallisuuspoikkeama)])}
              (lomake/ryhma {:otsikko "Poikkeaman käsittely"}
                            {:otsikko "Poikkeama kirjattu" :nimi :luotu :fmt pvm/pvm-aika-opt :tyyppi :string
                             :muokattava? (constantly false)
                             :uusi-rivi? true}
                            {:otsikko "Korjaavat toimenpiteet" :nimi :korjaavattoimenpiteet :tyyppi :komponentti
                             :palstoja 2
                             :uusi-rivi? true
                             :komponentti (fn [{:keys [muokkaa-lomaketta]}]
                                            [korjaavattoimenpiteet
                                             (rakenna-korjaavattoimenpiteet turvallisuuspoikkeama)
                                             turvallisuuspoikkeama
                                             toimenpiteet-virheet
                                             muokkaa-lomaketta])}
                            (when (istunto/ominaisuus-kaytossa? :urakan-tyotunnit)
                              {:otsikko "Urakan työtunnit"
                               :nimi :urakan-tyotunnit
                               :tyyppi :positiivinen-numero
                               :kokonaisluku? true})
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
     (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-turvallisuus (:id urakka))]
       (yleiset/wrap-if
         (not oikeus?)
         [yleiset/tooltip {} :%
          (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-turvallisuus)]
         [napit/uusi "Lisää turvallisuuspoikkeama"
          ;; todo: disabloi nappi, kysely käynnissä
          #(tiedot/uusi-turvallisuuspoikkeama (:id urakka))
          {:disabled (not oikeus?)}]))

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
    (komp/sisaan-ulos
      #(kartta-tiedot/kasittele-infopaneelin-linkit!
         {:turvallisuuspoikkeama {:toiminto
                                  (fn [turpo]
                                    (valitse-turvallisuuspoikkeama
                                      (:id @nav/valittu-urakka) (:id turpo)))
                                  :teksti "Avaa turvallisuuspoikkeama"}})
      #(kartta-tiedot/kasittele-infopaneelin-linkit! nil))
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M))
                      #(do
                         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)))
    (komp/ulos (kartta-tiedot/kuuntele-valittua! tiedot/valittu-turvallisuuspoikkeama))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-turvallisuuspoikkeama
         [turvallisuuspoikkeaman-tiedot]
         [turvallisuuspoikkeamalistaus])])))
