(ns harja.views.urakka.laadunseuranta.sanktiot-lomake
  "Sanktiolomake"
  (:require [clojure.string :as str]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.urakka.urakka :as uu-tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [reagent.core :refer [atom] :as r]))

(defn- toimenpide-valikon-nimi
  "Sanktion tyyppi vaikuttaa siihen näytetäänkö Kulun kohdistus alasvetovalikkoa ja siihen miten se nimetään, kun se näytetään.
  Muistutukselle ei näytetä Kulun kohdistus -valikkoa, jos muistutuksen tyyppinä on jotain Talvihoitoon liittyvää. Mutta
  jos muistutuksen tyyppinä on 'Muut hoitourakan tehtäväkokonaisuudet' tai 'Hallinnolliset laiminlyönnit',
  niin Kulun kohdistus valikko, joka on pohjimmiltaan toimenpideinstanssivalikko
  pitää näyttää. Muistukselle ei toki kulua tule, mutta se pitää kohdistaa johonkin toimenpiteeseen."
  [sanktion-tyyppi]
  (case sanktion-tyyppi
    "Muut hoitourakan tehtäväkokonaisuudet" "Toimenpide"
    "Hallinnolliset laiminlyönnit" "Toimenpide"
    "Kulun kohdistus"))

(defn- kopioi-maarattypvm-ja-kasittelyaika [rivi arvo]
  (-> rivi
    (assoc :maarattypvm arvo)
    (assoc-in [:laatupoikkeama :paatos :kasittelyaika] arvo)))

(defn- valittavat-kulun-kohdistukset [toimenpideinstanssit sanktion-tyyppi]
  (case sanktion-tyyppi
    "Muut hoitourakan tehtäväkokonaisuudet" (remove
                                              #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                                              toimenpideinstanssit)
    "Talvihoito, päätiet" (filter
                            #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                            toimenpideinstanssit)
    "Talvihoito, muut tiet" (filter
                              #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                              toimenpideinstanssit)
    "Sorateiden hoito ja ylläpito" (filter
                                     #(or (str/includes? (str/lower-case (:tpi_nimi %)) "soratie")
                                        (str/includes? (str/lower-case (:tpi_nimi %)) "sorateiden"))
                                     toimenpideinstanssit)
    "Liikenneympäristön hoito" (filter
                                 #(str/includes? (str/lower-case (:tpi_nimi %)) "liikenne")
                                 toimenpideinstanssit)
    toimenpideinstanssit))

(defn- viimeinen-hoitokausi-nykyhetkella?
  [urakan-tiedot pvm]
  (when (and urakan-tiedot pvm)
    (let [loppuvuosi (pvm/vuosi (:loppupvm urakan-tiedot))
          viimeinen (dec loppuvuosi) ; viimeisen hoitokauden alkuvuosi
          kauden-alku (pvm/hoitokauden-alkuvuosi pvm)]
      (= kauden-alku viimeinen))))

(defn- hae-sanktiotyyppi-idlla
  [sanktiotyypit tyyppi-id]
  (some #(when (= tyyppi-id (:id %)) %) sanktiotyypit))

(defn sanktio-lomake
  [sivupaneeli-auki?-atom lukutila? voi-muokata? & [{:keys [tallenna-fn]}]]
  (let [muokattu tiedot/valittu-sanktio
        suorasanktio? (:suorasanktio @muokattu)
        urakan-alkupvm (:alkupvm @nav/valittu-urakka)
        urakan-loppupvm (:loppupvm @nav/valittu-urakka)
        urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
        mhu25? (uu-tiedot/mhu25-urakka? @nav/valittu-urakka)
        muokataan-vanhaa? (or (some? (:id @muokattu)) (some? (:paikallinen-avain @muokattu)))
        tallennus-kaynnissa (atom false)
        urakka-id (:id @nav/valittu-urakka)
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?
        vesivaylaurakka? @tiedot-urakka/vesivaylaurakka?
        laskutuskuukaudet (tiedot/pyorayta-laskutuskuukausi-valinnat)
        yllapitokohteet (conj @laadunseuranta/urakan-yllapitokohteet-lomakkeelle {:id nil})
        mahdolliset-sanktiolajit @tiedot/valitun-urakan-sanktiolajit
        kaikki-sanktiotyypit @tiedot/sanktiotyypit 
        sanktio-konfiguraation-tila @tiedot/valitun-urakan-sanktio-konfiguraation-tila
        laskutuskuukausi-id (str "laskutuskuukausi-dropdown-" (gensym))
        liitteet-id (str "liiteet-element-id-" (gensym))
        mahdolliset-kulun-kohdistukset (tiedot/mahdolliset-kulun-kohdistukset suorasanktio? urakan-alkuvuosi muokattu)
        tyyppi-valinnat (vec (sanktio-domain/sanktiolaji->sanktiotyypit
                               (:laji @muokattu) kaikki-sanktiotyypit urakan-alkupvm))
        ;; Lukutila välitetään laatupoikkeaman sanktiolle sanktion tiedoissa.
        lukutila? (if (and (not suorasanktio?) (:lukutila? @muokattu))
                    (:lukutila? @muokattu)
                    lukutila?)
        talvisuolan-validointi-fn (fn [arvo sanktio]
                                    (when (and (not (nil? arvo))
                                            (= :talvisuolan_ylitys (:laji sanktio))
                                            (not (viimeinen-hoitokausi-nykyhetkella? @nav/valittu-urakka arvo)))
                                      (str "Sanktio voidaan määrätä ainostaan urakan viimeiselle hoitovuodelle " (pvm/vuosi (:loppupvm @nav/valittu-urakka)) ".")))
        urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
        urakan-loppuvuosi (pvm/vuosi urakan-loppupvm)
        urakan-loppukuukausi (pvm/kuukausi urakan-loppupvm)
        viimeinen-hoitovuoden-alkuvuosi (if (>= urakan-loppukuukausi 10)
                                          urakan-loppuvuosi
                                          (dec urakan-loppuvuosi))
        hoitovuodet (if (<= urakan-alkuvuosi viimeinen-hoitovuoden-alkuvuosi)
                      (vec (range urakan-alkuvuosi (inc viimeinen-hoitovuoden-alkuvuosi)))
                      [])
        hoitovuosi->teksti (fn [hoitovuosi]
                             (when (int? hoitovuosi)
                               (let [jarjestysnumero (inc (- hoitovuosi urakan-alkuvuosi))]
                                 (str jarjestysnumero ". hoitovuosi (" hoitovuosi " - " (inc hoitovuosi) ")"))))
        perintapvm->hoitovuosi (fn [perintapvm]
                                 (when perintapvm
                                   (let [vuosi (pvm/vuosi perintapvm)
                                         kuukausi (pvm/kuukausi perintapvm)
                                         hoitovuosi (if (>= kuukausi 10) vuosi (dec vuosi))]
                                     (when (some #{hoitovuosi} hoitovuodet)
                                       hoitovuosi))))]

    ;; Vaadi tarvittavat tiedot ennen rendausta
    (if (and (seq mahdolliset-sanktiolajit)
          (or (not yllapitokohdeurakka?)
            (and yllapitokohdeurakka? yllapitokohteet)))

      [:div
       [harja.ui.debug/debug @muokattu]

       [lomake/lomake
        {:otsikko "SANKTION TIEDOT"
         :otsikko-elementti :h4
         :ei-borderia? true
         :vayla-tyyli? true
         :luokka "padding-16 taustavari-taso3"
         :muokkaa! #(reset! tiedot/valittu-sanktio %)
         :validoi-alussa? false
         :voi-muokata? (and voi-muokata? (not lukutila?))
         :tarkkaile-ulkopuolisia-muutoksia? true
         :footer-fn (fn [sanktio]
                      [:span.nappiwrappi.flex-row
                       (when-not lukutila?
                         (if tallenna-fn
                           ;; Laatupoikkeaman sanktio: tallennetaan paikallisesti atomiin, ei kantaan
                           [napit/yleinen-ensisijainen
                            (str "Tallenna" (when muokataan-vanhaa? " muutokset"))
                            (fn []
                              (tallenna-fn (lomake/ilman-lomaketietoja @muokattu))
                              (reset! sivupaneeli-auki?-atom false)
                              (reset! tiedot/valittu-sanktio nil))
                            {:ikoni (ikonit/tallenna)
                             :disabled (or (not voi-muokata?)
                                         (not (lomake/voi-tallentaa? sanktio)))}]
                           ;; Suorasanktio: tallennetaan suoraan kantaan
                           [napit/palvelinkutsu-nappi
                            (str "Tallenna" (when muokataan-vanhaa? " muutokset"))
                            (fn []
                              (tiedot/tallenna-sanktio
                                (lomake/ilman-lomaketietoja @muokattu)
                                urakka-id
                                #(reset! sivupaneeli-auki?-atom false)))
                            {:luokka "nappi-ensisijainen"
                             :ikoni (ikonit/tallenna)
                             :disabled (or (not voi-muokata?)
                                         (not (lomake/voi-tallentaa? sanktio)))}]))
                       (when (and voi-muokata? (or (:id @muokattu) (:lukutila? @muokattu)) (not lukutila?))
                         [:button.nappi-kielteinen.oikealle
                          {:class (when @tallennus-kaynnissa "disabled")
                           :on-click
                           (fn [e]
                             (.preventDefault e)
                             (varmista-kayttajalta/varmista-kayttajalta
                               {:otsikko "Sanktion poistaminen"
                                :sisalto "Haluatko varmasti poistaa sanktion? Toimintoa ei voi perua."
                                :modal-luokka "varmistus-modal"
                                :hyvaksy "Poista"
                                :toiminto-fn (fn []
                                               (tiedot/poista-suorasanktio
                                                 (:id @muokattu)
                                                 urakka-id
                                                 #(reset! sivupaneeli-auki?-atom false)))}))}
                          (ikonit/livicon-trash) " Poista"])
                       [napit/peruuta (if lukutila?
                                        "Sulje"
                                        "Peruuta")
                        #(do
                           (reset! sivupaneeli-auki?-atom false)
                           (reset! tiedot/valittu-sanktio nil))]])}
        [(when-not vesivaylaurakka? ;; Vesiväylässä lajeina on vain sakko
           {:otsikko "Sanktion laji" :tyyppi :valinta :pakollinen? true
            ::lomake/col-luokka "col-xs-12"
            :uusi-rivi? true :nimi :laji
            :hae (comp keyword :laji)
            :aseta (fn [rivi arvo]
                (let [;; Ota vanhan tyypin id talteen, jotta valinta ei riipu mapin identiteetistä.
                      vanha-tyyppi-id (get-in rivi [:tyyppi :id])
                      rivi (-> rivi
                             (assoc :laji arvo)
                             (dissoc :tyyppi)
                             (assoc :tyyppi nil))
                      s-tyypit (tiedot/valitun-urakan-sanktiotyypit arvo)
                      vanha-tyyppi (hae-sanktiotyyppi-idlla s-tyypit vanha-tyyppi-id) 
                      yksi-tyyppi (first s-tyypit) 
                      toimenpideinstansseja (count @tiedot-urakka/urakan-toimenpideinstanssit)
                      rivi (cond
                                  ;; Ei saa resetoida toimenpideinsanssia nilliksi jos niitä on vain yksi
                                  ;; Koska alasvetovalinat ei lähetä uudesta valinnasta enää eventtiä
                             (and (= 1 (count s-tyypit)) yksi-tyyppi (not= toimenpideinstansseja 1))
                             (assoc rivi
                               :tyyppi yksi-tyyppi
                               :toimenpideinstanssi
                               (when (:toimenpidekoodi yksi-tyyppi)
                                 (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille (:toimenpidekoodi yksi-tyyppi)))))
                                  ;; Jos vanha tyyppi, löytyy sanktiolajin tyyppilistasta
                             (and (> (count s-tyypit) 1)
                               vanha-tyyppi)
                             (assoc rivi :tyyppi vanha-tyyppi
                               :toimenpideinstanssi (:toimenpidekoodi vanha-tyyppi))
                                  ;; Muussa tapauksessa, ei tehdä muutoksia
                             :else rivi)]
                  (if-not (sanktio-domain/muu-kuin-muistutus? rivi)
                    (assoc rivi :summa nil :toimenpideinstanssi nil :indeksi nil)
                    rivi)))
            :valinnat (vec mahdolliset-sanktiolajit)
            :valinta-nayta #(or (tiedot/valitun-urakan-sanktiolajin-nimi %) "- valitse laji -")
            :validoi [[:ei-tyhja "Valitse laji"]]})

         ;; Näytetään mahdollisesti Talvisuolan kokonaiskäytön ylitys sanktiosta tuleva ilmoitus
          (when (and (not lukutila?) (= :talvisuolan_ylitys (:laji @muokattu)))
            {:otsikko ""
             :nimi :talvisuolan-kokonaiskayton-ylitys
             ::lomake/col-luokka "col-xs-12"
             :tyyppi :komponentti
             :komponentti (fn [rivi]
                            [yleiset/info-laatikko :neutraali
                             [:span "Talvisuolan kokonaiskäytön ylitys -sanktio käsitellään urakan päätteeksi vastaanottotarkastuksessa ja sen voi kirjata Harjaan vasta viimeisenä hoitovuonna."]])})

         (when-not (or yllapitourakka? vesivaylaurakka? (= :laskutus_yli_laskutusrajan (:laji @muokattu)))
           (if (not lukutila?)
             {:otsikko "Tyyppi" :tyyppi :valinta
              :pakollinen? true
              :uusi-rivi? true
              ::lomake/col-luokka "col-xs-12"
              :nimi :tyyppi
              :aseta (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                       (if (<= urakan-alkuvuosi 2024)
                         (let [kohdistukset (tiedot/valittavat-kulun-kohdistukset
                                              @tiedot-urakka/urakan-toimenpideinstanssit
                                              (:nimi tyyppi))
                               tpi (cond
                                     ;; Jos toimenpidekoodi löytyy, käytä sitä
                                     tpk (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk))
                                     ;; Jos vain yksi vaihtoehto, esivalitse se
                                     (= 1 (count kohdistukset)) (:tpi_id (first kohdistukset))
                                     ;; Muuten säilytä nykyinen arvo
                                     :else (:toimenpideinstanssi sanktio))]
                           (-> sanktio
                             (assoc :tyyppi tyyppi)
                             (assoc :toimenpideinstanssi tpi)
                             (assoc :tpi_id tpi)))
                         (assoc sanktio :tyyppi tyyppi)))
              :valinta-arvo identity
              :aseta-vaikka-sama? true
              :valinnat tyyppi-valinnat
              :valinta-nayta (fn [arvo]
                               (if (or (nil? arvo) (nil? (:nimi arvo))) "Valitse sanktiotyyppi" (:nimi arvo)))
              :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]}

             ;; Näytetään lukutilassa valintakomponentin read-only -tilan sijasta tekstimuotoinen komponentti.
             ;; Vanhat poistetut sanktiotyypit eivät tule valintakomponenttiin vaihtoehdoiksi vanhoissa kirjauksissa,
             ;; joten näytetään tyyppi pelkkänä tekstinä.
             {:otsikko "Tyyppi" :tyyppi :teksti :nimi :tyyppi
              ::lomake/col-luokka "col-xs-12"
              :hae (comp :nimi :tyyppi)}))

         (when yllapitokohdeurakka?
           {:otsikko "Kohde" :tyyppi :valinta :nimi :yllapitokohde
            :pakollinen? false :muokattava? (constantly voi-muokata?)
            :uusi-rivi? true
            ::lomake/col-luokka "col-xs-12"
            :valinnat yllapitokohteet :jos-tyhja "Ei valittavia kohteita"
            :valinta-nayta (fn [arvo voi-muokata?]
                             (if (:id arvo)
                               (yllapitokohde-domain/yllapitokohde-tekstina
                                 arvo
                                 {:osoite {:tr-numero (:tr-numero arvo)
                                           :tr-alkuosa (:tr-alkuosa arvo)
                                           :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                           :tr-loppuosa (:tr-loppuosa arvo)
                                           :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                               (if (and voi-muokata? (not arvo))
                                 "- Valitse kohde -"
                                 (if (and voi-muokata? (nil? (:id arvo)))
                                   "Ei liity kohteeseen"
                                   ""))))})

         (when (and (not yllapitokohdeurakka?) (not vesivaylaurakka?) (not (= :laskutus_yli_laskutusrajan (:laji @muokattu))))
           {:otsikko "Tapahtumapaikka/kuvaus" :tyyppi :string :nimi :kohde
            :uusi-rivi? true
            :hae (comp :kohde :laatupoikkeama)
            ::lomake/col-luokka "col-xs-12"
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
            :pakollinen? true
            :muokattava? (if suorasanktio? (constantly voi-muokata?) (constantly false) )
            :validoi [[:ei-tyhja "Anna sanktion tapahtumapaikka/kuvaus"]]})


         (when yllapitourakka?
           {:otsikko "Puute tai laiminlyönti"
            :uusi-rivi? true
            :nimi :vakiofraasi
            :tyyppi :valinta
            :pitka-teksti? true
            ::lomake/col-luokka "col-xs-12"
            :valinta-arvo first
            :valinta-nayta second
            :valinnat sanktio-domain/+yllapidon-sanktiofraasit+})

         {:otsikko "Perustelu"
          :uusi-rivi? true
          :nimi :perustelu
          :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :muokattava? (if (not suorasanktio?) (constantly false) (constantly voi-muokata?))
          :hae (comp :perustelu :paatos :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
          :tyyppi :text :koko [80 3]
          :validoi [[:ei-tyhja "Anna perustelu"]]}

         ;; Kun sanktiolajina on "Laskutusrajan ylitys" niin näytetään "Ylityksen määrä" -kenttä
         (when (= :laskutus_yli_laskutusrajan (:laji @muokattu))
           {:otsikko "Ylityksen määrä (€)"
            :nimi :laskutusrajan-ylitys
            :tyyppi :euro
            :vaadi-positiivinen-numero? true
            ::lomake/col-luokka "col-xs-4"
            :hae #(when (:laskutusrajan-ylitys %) (Math/abs (:laskutusrajan-ylitys %)))
            :aseta (fn [rivi arvo] (-> rivi
                                     (assoc :laskutusrajan-ylitys arvo)
                                     (assoc :summa (* 0.2 arvo))))
            :pakollinen? true :uusi-rivi? true
            :validoi [[:ei-tyhja "Anna ylityksen määrä"]
                      [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

         ;; Kun sanktiolajina on "laskutusrajan ylitys" niin näytetään summa eri nimisenä ja eri kohdassa
         (when (= :laskutus_yli_laskutusrajan (:laji @muokattu))
          {:otsikko "Sanktion suuruus (20% ylittävästä laskutuksesta)"
           :nimi :summa
           :tyyppi :euro
           :kentan-arvon-luokka "fontti-20-kevyempi"
           :muokattava? (constantly false)
           :vaadi-positiivinen-numero? true
           ::lomake/col-luokka "col-xs-8"
           :hae #(when (:summa %) (Math/abs (:summa %)))
           :pakollinen? true :uusi-rivi? true})

         ;; Kulunkohdistusvalikkoa ei näytetä muistutuksille, jos niiden tyyppinä on jotain Talvihoitoon liittyvää.
         (when (or
                 (sanktio-domain/muu-kuin-muistutus? @muokattu)
                 (and (sanktio-domain/muistutus? @muokattu)
                   (contains? #{"Muut hoitourakan tehtäväkokonaisuudet"
                                "Liikenneympäristön hoito"
                                "Sorateiden hoito ja ylläpito"
                                "Hallinnolliset laiminlyönnit"} (get-in @muokattu [:tyyppi :nimi]))))
           (if (and (not mhu25?) (not lukutila?))
             {:otsikko (toimenpide-valikon-nimi (get-in @muokattu [:tyyppi :nimi])) ; "Kulun kohdistus"
              :pakollinen? true
              :disabled? (or (empty? @tiedot-urakka/urakan-toimenpideinstanssit)
                           (and (> urakan-alkuvuosi 2024)
                             (<= (count mahdolliset-kulun-kohdistukset) 1)))
              ::lomake/col-luokka "col-xs-12"
              :nimi :toimenpideinstanssi
              :tyyppi :valinta
              :valinta-arvo :tpi_id
              :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
              :valinnat mahdolliset-kulun-kohdistukset
              :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]}

             ;; Näytetään lukutilassa valintakomponentin read-only -tilan sijasta tekstimuotoinen komponentti.
             {:otsikko (str (toimenpide-valikon-nimi (get-in @muokattu [:tyyppi :nimi])))
              :tyyppi :string
              :nimi :toimenpideinstanssi
              :muokattava? (constantly false)
              ::lomake/col-luokka "col-xs-12"
              :hae (fn [rivi]
                     (let [tpi-id (:toimenpideinstanssi rivi)]
                       (or (some #(when (= (:tpi_id %) tpi-id) (:tpi_nimi %))
                             @tiedot-urakka/urakan-toimenpideinstanssit)
                         "")))}))

         (apply lomake/ryhma {:rivi? true}
           (keep identity [(when (and (sanktio-domain/muu-kuin-muistutus? @muokattu) (not (= :laskutus_yli_laskutusrajan (:laji @muokattu))))
                             {:otsikko "Sanktion suuruus" :nimi :summa :tyyppi :euro
                              :vaadi-positiivinen-numero? true
                              ::lomake/col-luokka "col-xs-4"
                              :hae #(when (:summa %) (Math/abs (:summa %)))
                              :pakollinen? true :uusi-rivi? true
                              :validoi [[:ei-tyhja "Anna summa"]
                                        [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

                           ;; MHU21-> urakoille ei näytetä indeksiä
                           (when (and (<= urakan-alkuvuosi 2020) (sanktio-domain/muu-kuin-muistutus? @muokattu))
                             {:otsikko (str "Indeksi") :nimi :indeksi
                              :tyyppi :valinta
                              ::lomake/col-luokka "col-xs-4"
                              :muokattava? (constantly (not lukutila?))
                              :hae (if (tiedot-urakka/indeksi-kaytossa-sakoissa?) :indeksi (constantly nil))
                              :disabled? (not (tiedot-urakka/indeksi-kaytossa-sakoissa?))
                              :valinnat (if (and (tiedot-urakka/indeksi-kaytossa-sakoissa?) (not (nil? (:indeksi @nav/valittu-urakka))))
                                          [(:indeksi @nav/valittu-urakka) nil]
                                          [nil])
                              :valinta-nayta #(or % "Ei indeksiä")})]))

         (lomake/ryhma {:rivi? true}
           {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
            :pakollinen? true
            ::lomake/col-luokka "col-xs-3"
            :hae (comp :aika :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
            :fmt pvm/pvm-opt :tyyppi :pvm
            :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

           ;; MHU25 urakoilla ei ole käsittelyaikaa enää, vaan sen korvaa Määrätty pvm
           (if (not mhu25?)
            {:otsikko "Käsitelty" :nimi :kasittelyaika
             :pakollinen? true
             :muokattava? (if (not suorasanktio?) (constantly false) (constantly voi-muokata?)) ;; Laatupoikkeaman kautta käsittelyaika on aina sama, kuin laatupoikkeamalla.
             ::lomake/col-luokka "col-xs-3"
             :hae (comp :kasittelyaika :paatos :laatupoikkeama)
             :aseta (fn [rivi arvo]
                      (let [rivi (cond-> rivi
                                   ;; Jos laskutuskuukautta (:perintpvm) ei ole vielä valittu, niin asetetaan
                                   ;; esivalintana laskutuskuukaudelle valittu käsittelypvm
                                   (nil? (:laskutuskuukausi-komp-tiedot rivi))
                                   (assoc-in [:perintapvm] arvo))]
                        (kopioi-maarattypvm-ja-kasittelyaika rivi arvo)))
             :fmt pvm/pvm-opt :tyyppi :pvm
             :validoi [[:ei-tyhja "Valitse päivämäärä"]
                       (partial talvisuolan-validointi-fn)]}

             {:otsikko "Määrätty" :nimi :maarattypvm
              :pakollinen? true
              ::lomake/col-luokka "col-xs-3"
              :aseta (fn [rivi arvo]
                       (let [rivi (cond-> rivi
                                    ;; Jos laskutuskuukautta (:perintpvm) ei ole vielä valittu, niin asetetaan
                                    ;; hoitokauden syyskuun 15. päivä
                                    (nil? (:laskutuskuukausi-komp-tiedot rivi))
                                    (assoc-in [:perintapvm] (pvm/hoitokauden-loppupvm (pvm/vuosi (second (pvm/paivamaaran-hoitokausi arvo))))))]
                         (kopioi-maarattypvm-ja-kasittelyaika rivi arvo)))
              :fmt pvm/pvm-opt :tyyppi :pvm
              :validoi [[:ei-tyhja "Valitse päivämäärä"]
                        (partial talvisuolan-validointi-fn)]})

           ;; MHU25 urakoille ei näytetä laskutuskuukautta
           (if (<= urakan-alkuvuosi 2024)
             (if (and voi-muokata? (not lukutila?))
               {:otsikko "Laskutuskuukausi"
                :label-for-id laskutuskuukausi-id
                :nimi :perintapvm
                :pakollinen? true
                :tyyppi :komponentti
                ::lomake/col-luokka "col-xs-6"
                :huomauta [[:urakan-aikana-ja-hoitokaudella]]
                :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                               (let [perintapvm (get-in data [:perintapvm])]
                                 [:<>
                                  [yleiset/livi-pudotusvalikko
                                   {:data-cy "koontilaskun-kk-dropdown"
                                    :vayla-tyyli? true
                                    :skrollattava? true
                                    :elementin-id laskutuskuukausi-id
                                    :pakollinen? true
                                    :valinta (or
                                               ;; Näytetään valintana joko valittua laskutuskuukautta, tai
                                               (-> data :laskutuskuukausi-komp-tiedot)
                                               ;; jos käyttäjä ei tehnyt/muuttanut valintaa, käytetään tietokannasta haettua arvoa
                                               (when perintapvm
                                                 (some #(when (and
                                                                (= (pvm/vuosi perintapvm)
                                                                   (:vuosi %))
                                                                (= (pvm/kuukausi perintapvm)
                                                                   (:kuukausi %))) %)
                                                   laskutuskuukaudet)))
                                    :valitse-fn #(muokkaa-lomaketta
                                                   (assoc data
                                                     ;; Tallennetaan tieto koko laskutuskuukauden valinnasta erikseen, jotta
                                                     ;;  sitä voi hyödyntää muualla lomakkeessa.
                                                     :laskutuskuukausi-komp-tiedot %
                                                     ;; Varsinainen perintapvm poimitaan valitun laskutuskuukauden pvm-kentästä.
                                                     :perintapvm (:pvm %)))
                                    :format-fn :teksti}
                                   laskutuskuukaudet]
                                  ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                                  (when (not yllapitourakka?)
                                    [:div.small-caption.padding-vertical-4 "Näkyy laskutusyhteenvedolla"])]))}

               {:otsikko "Laskutuskuukausi"
                :nimi :perintapvm
                :fmt (fn [pvm]
                       ;; Lukutilassa haetaan näytettävä laskutuskuukausi suoraan lomakkeen avaimesta
                       (when pvm
                         (some #(when (and
                                        (= (pvm/vuosi pvm) (pvm/vuosi (:pvm %)))
                                        (= (pvm/kuukausi pvm) (pvm/kuukausi (:pvm %)))) (:teksti %))
                           laskutuskuukaudet)))
                :pakollinen? true
                :tyyppi :pvm
                ::lomake/col-luokka "col-xs-6"})
             ;; MHU25 urakoille näytetään perintäpäivä hoitokautena
             {:otsikko "Kohdistuu hoitovuodelle"
              :nimi :perintapvm
              :pakollinen? true
              :tyyppi :valinta
              :valinnat hoitovuodet
              :hae #(perintapvm->hoitovuosi (:perintapvm %))
              :aseta (fn [rivi hoitovuosi]
                       (assoc rivi :perintapvm
                         (when hoitovuosi
                           (pvm/hoitokauden-alkupvm hoitovuosi))))
              :valinta-nayta #(or (hoitovuosi->teksti %) " - valitse hoitovuosi -")
              ::lomake/col-luokka "col-xs-6"}))

          ;; MHU25 urakoille näytetään määräystapa valintana.
          (when mhu25?
            (if (not lukutila?)
              {:otsikko "Määräystapa"
               :radio-luokka "maaraystapa-ei-marginia"
               :nimi :maaraystapa
               :tyyppi :radio-group
               :pakollinen? true
               :uusi-rivi? true
               :nayta-rivina? true
               ::lomake/col-luokka "col-xs-12"
               :vaihtoehdot ["tyomaakokous" "valikatselmus"]
               :vaihtoehto-nayta {"tyomaakokous" "Työmaakokous"
                                  "valikatselmus" "Välikatselmus"}}

              {:otsikko "Määräystapa"
               :nimi :maaraystapa
               :tyyppi :teksti
               :muokattava? (constantly false)
               ::lomake/col-luokka "col-xs-12"
               :fmt (fn [arvo]
                      (case arvo
                        "tyomaakokous" "Työmaakokous"
                        "valikatselmus" "Välikatselmus"
                        arvo))}))

         {:otsikko (if mhu25? "Käsittely ja laskutus" "Käsittelytapa")
          :nimi :kasittelytapa
          :tyyppi :valinta
          :muokattava? (if mhu25? (constantly false) (constantly voi-muokata?))
          :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :valinnat (if mhu25? sanktio-domain/kasittelytavat-mhu25 sanktio-domain/kasittelytavat)
          :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")}

         (when (= :muu (:kasittelytapa @muokattu))
           {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
            ::lomake/col-luokka "col-xs-12"
            :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
            :tyyppi :string
            :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

         (when (and (not lukutila?)
                 (:suorasanktio @muokattu))
           {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
            :tyyppi :komponentti
            :label-for-id liitteet-id
            ::lomake/col-luokka "col-xs-12"
            :komponentti (fn [_]
                           [liitteet/liitteet-ja-lisays urakka-id (get-in @muokattu [:laatupoikkeama :liitteet])
                            {:uusi-liite-atom (r/wrap (:uusi-liite @tiedot/valittu-sanktio)
                                                #(swap! tiedot/valittu-sanktio
                                                   (fn [] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] %))))
                             :uusi-liite-teksti "Lisää liite"
                             :elementin-id liitteet-id
                             :nayta-koko? true
                             :salli-poistaa-lisatty-liite? true
                             :poista-lisatty-liite-fn #(swap! tiedot/valittu-sanktio
                                                         (fn [] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] nil)))
                             :salli-poistaa-tallennettu-liite? true
                             :poista-tallennettu-liite-fn
                             (fn [liite-id]
                               (liitteet/poista-liite-kannasta
                                 {:urakka-id urakka-id
                                  :domain :laatupoikkeama
                                  :domain-id (get-in @tiedot/valittu-sanktio [:laatupoikkeama :id])
                                  :liite-id liite-id
                                  :poistettu-fn (fn []
                                                  (let [liitteet (get-in @muokattu [:laatupoikkeama :liitteet])]
                                                    (swap! tiedot/valittu-sanktio assoc-in [:laatupoikkeama :liitteet]
                                                      (filter (fn [liite]
                                                                (not= (:id liite) liite-id))
                                                        liitteet))))}))}])})
         (when lukutila?
           {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
            :tyyppi :komponentti
            :uusi-rivi? true
            ::lomake/col-luokka "col-xs-12"
            :muokattava? (constantly false)
            :komponentti (fn [_]
                           (if (seq (get-in @muokattu [:laatupoikkeama :liitteet]))
                             (doall
                               (for [l (get-in @muokattu [:laatupoikkeama :liitteet])]
                                 ^{:key l}
                                 [liitteet/liitetiedosto l {:salli-poisto? false
                                                            :nayta-koko? true}]))
                             (str "Ei liitettä" (when (not suorasanktio?) " (käytä laatupoikkeaman liitteitä)"))))})

         (when lukutila?
           {:otsikko "Kirjaaja" :nimi :tekijanimi
            :uusi-rivi? true
            :hae (comp :tekijanimi :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
            :tyyppi :string
            ::lomake/col-luokka "col-xs-12"
            :muokattava? (constantly false)})]
        @muokattu]]
      (case sanktio-konfiguraation-tila
        :haku-kaynnissa [ajax-loader "Ladataan..."]
        :haku-epaonnistui [:div
                           [:p "Sanktioita ei voitu ladata juuri nyt."]
                           [:p "Yritä hetken kuluttua uudelleen. Jos ongelma jatkuu, ota yhteyttä Harja-tukeen."]]
        :ei-konfiguraatiota [:div
                             [:p "Sanktioita ei ole määritelty tälle urakalle valitulla hoitokaudella."]
                             [:p "Ota yhteyttä Harja-tukeen jotta asia saadaan korjattua."]]
        [ajax-loader "Ladataan..."]))))
