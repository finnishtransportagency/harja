(ns harja.views.urakka.laadunseuranta.sanktiot-lomake
  "Sanktiolomake"
  (:require
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.fmt :as fmt]    
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [reagent.core :as r]))

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

(defn- hae-sanktiotyyppi-idlla
  [sanktiotyypit tyyppi-id]
  (some #(when (= tyyppi-id (:id %)) %) sanktiotyypit))


(defn- lukutilan-kenttakuvaus
  [kenttakuvaus]
  (assoc kenttakuvaus
    :tyyppi :string
    :muokattava? (constantly false)))

(defn- sanktion-laji-ja-tyyppikentat
  [sanktio lukutila? mahdolliset-sanktiolajit tyyppi-valinnat
   urakan-alkuvuosi urakan-toimenpideinstanssit lomakekonteksti]
  [(when-not (:vesivaylaurakka? lomakekonteksti) ;; Vesiväylässä lajeina on vain sakko
     {:otsikko "Sanktion laji" :tyyppi :valinta :pakollinen? true
      ::lomake/col-luokka "col-xs-12"
      :uusi-rivi? true :nimi :laji
      :hae (comp keyword :laji)
      :aseta (fn [rivi arvo]
               (sanktio-domain/vaihda-sanktiolajia
                 rivi
                 arvo
                 (tiedot/valitun-urakan-sanktiotyypit arvo)
                 urakan-toimenpideinstanssit))
      :valinnat (vec mahdolliset-sanktiolajit)
      :valinta-nayta #(or (tiedot/valitun-urakan-sanktiolajin-nimi %) "- valitse laji -")
      :validoi [[:ei-tyhja "Valitse laji"]]})

   ;; Näytetään mahdollisesti Talvisuolan kokonaiskäytön ylitys sanktiosta tuleva ilmoitus
   (when (and (not lukutila?) (:nayta-talvisuola-ilmoitus? lomakekonteksti))
     {:otsikko ""
      :nimi :talvisuolan-kokonaiskayton-ylitys
      ::lomake/col-luokka "col-xs-12"
      :tyyppi :komponentti
      :komponentti (fn [_rivi]
                     [yleiset/info-laatikko :neutraali
                      [:span "Talvisuolan kokonaiskäytön ylitys -sanktio käsitellään urakan päätteeksi vastaanottotarkastuksessa ja sen voi kirjata Harjaan vasta viimeisenä hoitovuonna."]])})

   (when (:nayta-tyyppi? lomakekonteksti)
     (let [tyyppikentta {:otsikko "Tyyppi" :tyyppi :valinta
                         :pakollinen? true
                         :uusi-rivi? true
                         ::lomake/col-luokka "col-xs-12"
                         :nimi :tyyppi
                         :aseta (fn [sanktio tyyppi]
                                  (sanktio-domain/vaihda-sanktiotyyppia
                                    sanktio
                                    tyyppi
                                    urakan-alkuvuosi
                                    @tiedot-urakka/urakan-toimenpideinstanssit
                                    (tiedot/valittavat-kulun-kohdistukset
                                      @tiedot-urakka/urakan-toimenpideinstanssit
                                      (:nimi tyyppi))))
                         :valinta-arvo identity
                         :aseta-vaikka-sama? true
                         :valinnat tyyppi-valinnat
                         :valinta-nayta (fn [arvo]
                                          (if (or (nil? arvo) (nil? (:nimi arvo)))
                                            "Valitse sanktiotyyppi"
                                            (sanktiotyyppi-domain/sanktiotyypin-nimi
                                              (tiedot/valitun-urakan-sanktiolajin-nimi (:laji sanktio))
                                              arvo)))
                         :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]}
           lukutilan-tyyppikentta (assoc (lukutilan-kenttakuvaus tyyppikentta)
                                    :hae (fn [rivi]
                                           (sanktiotyyppi-domain/sanktiotyypin-nimi
                                             (tiedot/valitun-urakan-sanktiolajin-nimi (:laji rivi))
                                             (:tyyppi rivi))))]
       (if lukutila? lukutilan-tyyppikentta tyyppikentta)))])

(defn- yllapitokohde-kentta
  [yllapitokohdeurakka? yllapitokohteet voi-muokata?]
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
                            ""))))}))

(defn- tapahtumapaikka-kentta
  [lomakekonteksti voi-muokata?]
  (when (:nayta-tapahtumapaikka? lomakekonteksti)
    {:otsikko "Tapahtumapaikka/kuvaus" :tyyppi :string :nimi :kohde
     :uusi-rivi? true
     :hae (comp :kohde :laatupoikkeama)
     ::lomake/col-luokka "col-xs-12"
     :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
     :pakollinen? true
     :muokattava? (constantly voi-muokata?)
     :validoi [[:ei-tyhja "Anna sanktion tapahtumapaikka/kuvaus"]]}))

(defn- puute-tai-laiminlyonti-kentta
  [lomakekonteksti]
  (when (:nayta-puute-tai-laiminlyonti? lomakekonteksti)
    {:otsikko "Puute tai laiminlyönti"
     :uusi-rivi? true
     :nimi :vakiofraasi
     :tyyppi :valinta
     :pitka-teksti? true
     ::lomake/col-luokka "col-xs-12"
     :valinta-arvo first
     :valinta-nayta second
     :valinnat sanktio-domain/+yllapidon-sanktiofraasit+}))

(defn- perustelu-kentta
  [lomakekonteksti voi-muokata?]
  (when (:nayta-perustelu? lomakekonteksti)
    {:otsikko "Perustelu"
     :uusi-rivi? true
     :nimi :perustelu
     :pakollinen? true
     ::lomake/col-luokka "col-xs-12"
     :muokattava? (constantly voi-muokata?)
     :hae (comp :perustelu :paatos :laatupoikkeama)
     :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
     :tyyppi :text :koko [80 3]
     :validoi [[:ei-tyhja "Anna perustelu"]]}))

(defn- omailmoitus-kentta
  [omailmoitus-sallittu?]
  (when omailmoitus-sallittu?
    {:otsikko ""
     :nimi :omailmoitettu
     :tyyppi :checkbox
     :uusi-rivi? true
     :label-luokka "fontti-12"
     ::lomake/col-luokka "col-xs-12"
     :hae :omailmoitettu
     :aseta (fn [rivi arvo] (assoc rivi :omailmoitettu (boolean arvo)))
     :teksti "Kyseessä on urakoitsijan itse ilmoittama laadunalitus tai tehtävän laiminlyönti, joka alentaa sanktiota 50 %"}))

(defn- summan-maarityksen-kentat
  [laskutus-ilman-laskutuskelpoisuutta? laskettu-maaritys laskentaparametrit
   manuaalinen-laskettavan-sisartyyppi? c-ryhman-maaritys kiintea-profiilisumma?
   lopullinen-maara aikakausikonteksti sanktio lukutila? indeksi-kaytossa? urakan-indeksi]
  (apply lomake/ryhma {:rivi? (not laskettu-maaritys)}
    (keep identity [(when laskettu-maaritys
                      {:otsikko (if laskutus-ilman-laskutuskelpoisuutta?
                                  "Laskutettu summa"
                                  (sanktion-laskenta/syotteen-otsikko laskentaparametrit))
                       :nimi :laskettava-syote
                       :tyyppi (if laskutus-ilman-laskutuskelpoisuutta? :euro :numero)
                       :max-desimaalit (:desimaalit laskentaparametrit)
                       :data-cy "sanktio-laskettava-syote"
                       ::lomake/col-luokka "col-xs-3"
                       :hae sanktion-laskenta/laskettava-syote
                       :aseta (fn [rivi arvo] (assoc rivi :laskettava-syote arvo))
                       :pakollinen? true :uusi-rivi? true
                       :validoi [(fn [arvo _rivi _taulukko]
                                   (sanktion-laskenta/syotteen-virhe laskentaparametrit arvo))]})
                    (when laskettu-maaritys
                      {:otsikko (if laskutus-ilman-laskutuskelpoisuutta?
                                  "Sanktion suuruus (20% summasta, joka ei ole ollut laskutuskelpoinen)"
                                  "Sanktion suuruus")
                       :nimi :laskettu-sanktion-maara
                       :tyyppi :euro
                       :kentan-arvon-luokka "fontti-14"
                       :muokattava? (constantly false)
                       :uusi-rivi? true
                       ::lomake/col-luokka "col-xs-12"
                       :hae #(sanktion-laskenta/laskettu-tulos laskettu-maaritys %)})
                    (when (and (sanktio-domain/muu-kuin-muistutus? sanktio)
                               (not laskettu-maaritys)
                               (not (= :laskutus_yli_laskutusrajan (:laji sanktio))))
                      {:otsikko (if manuaalinen-laskettavan-sisartyyppi?
                                  "Sanktion määrä (€)"
                                  "Sanktion suuruus")
                       :nimi :summa :tyyppi :euro
                       :muokattava? (constantly (not kiintea-profiilisumma?))
                       :vaadi-positiivinen-numero? true
                       ::lomake/col-luokka "col-xs-4"
                       :hae #(if kiintea-profiilisumma?
                               (when-let [summa lopullinen-maara]
                                 (Math/abs summa))
                               (when (:summa %)
                                 (Math/abs (:summa %))))
                       :vihje (when c-ryhman-maaritys
                                (str "-" (fmt/euro-opt false (:summa-euroina c-ryhman-maaritys))
                                  " " (:ohjeteksti c-ryhman-maaritys)))
                       :pakollinen? true :uusi-rivi? true
                       :validoi [[:ei-tyhja "Anna summa"]
                                 [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})
                    (when (and (:nayta-indeksi? aikakausikonteksti)
                               (sanktio-domain/muu-kuin-muistutus? sanktio))
                      {:otsikko (str "Indeksi") :nimi :indeksi
                       :tyyppi :valinta
                       ::lomake/col-luokka "col-xs-4"
                       :muokattava? (constantly (not lukutila?))
                       :hae (if indeksi-kaytossa? :indeksi (constantly nil))
                       :disabled? (not indeksi-kaytossa?)
                       :valinnat (if (and indeksi-kaytossa? (not (nil? urakan-indeksi)))
                                   [urakan-indeksi nil]
                                   [nil])
                       :valinta-nayta #(or % "Ei indeksiä")})])))

(defn- kulun-kohdistus-kentta
  [sanktio aikakausikonteksti lukutila?
   mahdolliset-kulun-kohdistukset hoidonjohtopalkkio-tr
   urakan-toimenpideinstanssit]
  ;; Kulunkohdistusvalikkoa ei näytetä muistutuksille, jos niiden tyyppinä on jotain Talvihoitoon liittyvää.
  (when (or
          (sanktio-domain/muu-kuin-muistutus? sanktio)
          (and (sanktio-domain/muistutus? sanktio)
            (contains? #{"Muut hoitourakan tehtäväkokonaisuudet"
                         "Liikenneympäristön hoito"
                         "Sorateiden hoito ja ylläpito"
                         "Hallinnolliset laiminlyönnit"} (get-in sanktio [:tyyppi :nimi]))))
    (let [kulun-kohdistus {:otsikko (toimenpide-valikon-nimi (get-in sanktio [:tyyppi :nimi])) ; "Kulun kohdistus"
                           :pakollinen? true
                           :disabled? (or (empty? @urakan-toimenpideinstanssit)
                                        (and (:maaraystapa? aikakausikonteksti)
                                          (<= (count mahdolliset-kulun-kohdistukset) 1)))
                           ::lomake/col-luokka "col-xs-12"
                           :nimi :toimenpideinstanssi
                           :tyyppi :valinta
                           :valinta-arvo :tpi_id
                           :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
                           :valinnat mahdolliset-kulun-kohdistukset
                           :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]}
          lukutilan-kulun-kohdistus (assoc (lukutilan-kenttakuvaus kulun-kohdistus)
                                      :hae (if (:maaraystapa? aikakausikonteksti)
                                             #(:tehtavaryhma_nimi hoidonjohtopalkkio-tr)
                                             #(some (fn [tpi]
                                                      (when (= (:tpi_id tpi) (:toimenpideinstanssi %))
                                                        (:tpi_nimi tpi)))
                                                @urakan-toimenpideinstanssit)))]
      (if (and (not (:maaraystapa? aikakausikonteksti)) (not lukutila?))
        kulun-kohdistus
        lukutilan-kulun-kohdistus))))

(defn- laskutuskuukausi-muokkauskuvaus
  [laskutuskuukausi-id laskutuskuukaudet yllapitourakka?]
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
                                        ;; sitä voi hyödyntää muualla lomakkeessa.
                                        :laskutuskuukausi-komp-tiedot %
                                        ;; Varsinainen perintapvm poimitaan valitun laskutuskuukauden pvm-kentästä.
                                        :perintapvm (:pvm %)))
                       :format-fn :teksti}
                      laskutuskuukaudet]
                     ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                     (when (not yllapitourakka?)
                       [:div.small-caption.padding-vertical-4 "Näkyy laskutusyhteenvedolla"])]))})

(defn- laskutuskuukausi-lukutilakuvaus
  [muokkauskuvaus laskutuskuukaudet]
  (-> (lukutilan-kenttakuvaus muokkauskuvaus)
    (dissoc :label-for-id :huomauta :komponentti)
    (assoc :fmt (fn [pvm]
                  ;; Lukutilassa haetaan näytettävä laskutuskuukausi suoraan lomakkeen avaimesta
                  (when pvm
                    (some #(when (and
                                   (= (pvm/vuosi pvm) (pvm/vuosi (:pvm %)))
                                   (= (pvm/kuukausi pvm) (pvm/kuukausi (:pvm %)))) (:teksti %))
                      laskutuskuukaudet))))))

(defn- paivamaarat-ja-perinta-kentat
  [laskutus-ilman-laskutuskelpoisuutta? aikakausikonteksti voi-muokata? lukutila?
   laskutuskuukausi-id laskutuskuukaudet yllapitourakka? hoitovuodet
   perintapvm->hoitovuosi hoitovuosi->teksti talvisuolan-validointi-fn]
  (lomake/ryhma {:rivi? true}
    (when-not laskutus-ilman-laskutuskelpoisuutta?
      {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
       :pakollinen? true
       ::lomake/col-luokka "col-xs-3"
       :hae (comp :aika :laatupoikkeama)
       :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
       :fmt pvm/pvm-opt :tyyppi :pvm
       :validoi [[:ei-tyhja "Valitse päivämäärä"]]})

    ;; MHU25 urakoilla ei ole käsittelyaikaa enää, vaan sen korvaa Määrätty pvm
    (if (not (:maaraystapa? aikakausikonteksti))
      {:otsikko "Käsitelty" :nimi :kasittelyaika
       :pakollinen? true
       :muokattava? (constantly voi-muokata?) ;; Laatupoikkeaman kautta käsittelyaika on aina sama, kuin laatupoikkeamalla.
       ::lomake/col-luokka "col-xs-3"
       :hae (comp :kasittelyaika :paatos :laatupoikkeama)
       :aseta (fn [rivi arvo]
                (sanktio-domain/aseta-kasittely-tai-maarattypvm
                  rivi arvo :kasittelyaika))
       :fmt pvm/pvm-opt :tyyppi :pvm
       :validoi [[:ei-tyhja "Valitse päivämäärä"]
                 (partial talvisuolan-validointi-fn)]}

      {:otsikko "Määrätty" :nimi :maarattypvm
       :pakollinen? true
       ::lomake/col-luokka "col-xs-3"
       :aseta (fn [rivi arvo]
                (sanktio-domain/aseta-kasittely-tai-maarattypvm
                  rivi arvo :maarattypvm))
       :fmt pvm/pvm-opt :tyyppi :pvm
       :validoi [[:ei-tyhja "Valitse päivämäärä"]
                 (partial talvisuolan-validointi-fn)]})

    ;; MHU25 urakoille ei näytetä laskutuskuukautta
    (if (= :laskutuskuukausi (:perinta-tapa aikakausikonteksti))
      (let [muokkauskuvaus (laskutuskuukausi-muokkauskuvaus
                             laskutuskuukausi-id laskutuskuukaudet yllapitourakka?)]
        (if (and voi-muokata? (not lukutila?))
          muokkauskuvaus
          (laskutuskuukausi-lukutilakuvaus muokkauskuvaus laskutuskuukaudet)))
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
       ::lomake/col-luokka "col-xs-6"})))

(defn- kasittely-kentat
  [aikakausikonteksti lukutila? voi-muokata? sanktio]
  [(when (:maaraystapa? aikakausikonteksti)
     (let [muokkauskuvaus {:otsikko "Määräystapa"
                           :radio-luokka "maaraystapa-ei-marginia"
                           :nimi :maaraystapa
                           :tyyppi :radio-group
                           :pakollinen? true
                           :uusi-rivi? true
                           :nayta-rivina? true
                           ::lomake/col-luokka "col-xs-12"
                           :vaihtoehdot [:tyomaakokous :valikatselmus]
                           :vaihtoehto-nayta {:tyomaakokous "Työmaakokous"
                                              :valikatselmus "Välikatselmus"}}
           lukutilan-kuvaus (assoc (lukutilan-kenttakuvaus muokkauskuvaus)
                              :fmt (fn [arvo]
                                     (case arvo
                                       :tyomaakokous "Työmaakokous"
                                       :valikatselmus "Välikatselmus"
                                       arvo)))]
       (if lukutila? lukutilan-kuvaus muokkauskuvaus)))

   {:otsikko "Käsittelytapa"
    :nimi :kasittelytapa
    :tyyppi :valinta
    :muokattava? (if (not (:maaraystapa? aikakausikonteksti)) (constantly voi-muokata?) (constantly false))
    :pakollinen? true
    ::lomake/col-luokka "col-xs-12"
    :valinnat (:kasittelytavat aikakausikonteksti)
    :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")}

   (when (= :muu (:kasittelytapa sanktio))
     {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
      ::lomake/col-luokka "col-xs-12"
      :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
      :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
      :tyyppi :string
      :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})])

(defn- liitteet-muokkaustilassa-kentta
  [lukutila? urakka-id liitteet-id muokattu]
  (when (and (not lukutila?) (:suorasanktio @muokattu))
    {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
     :tyyppi :komponentti
     :label-for-id liitteet-id
     ::lomake/col-luokka "col-xs-12"
     :komponentti (fn [_]
                    [liitteet/liitteet-ja-lisays urakka-id (get-in @muokattu [:laatupoikkeama :liitteet])
                     {:uusi-liite-atom (r/wrap (:uusi-liite @tiedot/valittu-sanktio)
                                          #(swap! tiedot/valittu-sanktio
                                            (fn [_] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] %))))
                      :uusi-liite-teksti "Lisää liite"
                      :elementin-id liitteet-id
                      :nayta-koko? true
                      :salli-poistaa-lisatty-liite? true
                      :poista-lisatty-liite-fn #(swap! tiedot/valittu-sanktio
                                                  (fn [_] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] nil)))
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
                                                 liitteet))))}))}])}))

(defn- liitteet-lukutilassa-kentta
  [lukutila? suorasanktio? muokattu]
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
                      (str "Ei liitettä" (when (not suorasanktio?) " (käytä laatupoikkeaman liitteitä)"))))}))

(defn- kirjaaja-kentta
  [lukutila?]
  (when lukutila?
    {:otsikko "Kirjaaja" :nimi :tekijanimi
     :uusi-rivi? true
     :hae (comp :tekijanimi :laatupoikkeama)
     :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
     :tyyppi :string
     ::lomake/col-luokka "col-xs-12"
     :muokattava? (constantly false)}))

(defn sanktio-lomake
  [sivupaneeli-auki?-atom lukutila? voi-muokata? & [{:keys [tallenna-fn]}]]
  (let [muokattu tiedot/valittu-sanktio
        suorasanktio? (:suorasanktio @muokattu)
        ;; Laatupoikkeaman kautta tehdyn (ei-suora)sanktion poisto vaatii erillisen "poisto"-oikeuden.
        ;; Suorasanktion poisto riittää kirjoitusoikeudella (voi-muokata?), joten sille tämä on aina tosi.
        saa-poistaa? (or suorasanktio?
                       (oikeudet/on-muu-oikeus? "poisto" oikeudet/urakat-laadunseuranta-sanktiot
                         (:id @nav/valittu-urakka)))
        urakan-alkupvm (:alkupvm @nav/valittu-urakka)
        urakan-loppupvm (:loppupvm @nav/valittu-urakka)
        urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
        aikakausikonteksti (sanktio-domain/aikakausikonteksti
                 urakan-alkuvuosi
                 (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi))
                 (:tyyppi @nav/valittu-urakka))
        muokataan-vanhaa? (or (some? (:id @muokattu)) (some? (:paikallinen-avain @muokattu)))
        urakka-id (:id @nav/valittu-urakka)
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?
        vesivaylaurakka? @tiedot-urakka/vesivaylaurakka?
        laskutuskuukaudet (tiedot/pyorayta-laskutuskuukausi-valinnat)
        yllapitokohteet (conj @laadunseuranta/urakan-yllapitokohteet-lomakkeelle {:id nil})
        ;; Arvonvähennys kuuluu urakan sanktioihin, mutta ei tähän lomakkeelle, koska sille on oma lomakkeensa mhu25+ ja 2026 hoitovuodesta alkaen
        mahdolliset-sanktiolajit (if (not (:salli-arvonvahennys? aikakausikonteksti))
                                   (remove #(= :arvonvahennyssanktio %) @tiedot/valitun-urakan-sanktiolajit)
                                   @tiedot/valitun-urakan-sanktiolajit)
        sanktio-konfiguraation-tila @tiedot/valitun-urakan-sanktio-konfiguraation-tila
        laskutuskuukausi-id (str "laskutuskuukausi-dropdown-" (gensym))
        liitteet-id (str "liiteet-element-id-" (gensym))
        ;; mhu -24 alkaviin urakoihin asti sanktio kohdistuu toimenpideinstanssiin.
        mahdolliset-kulun-kohdistukset (tiedot/mahdolliset-kulun-kohdistukset suorasanktio? urakan-alkuvuosi muokattu)
        ;;mhu -25 alkaen, sanktio kohdistuu tehtäväryhmään "G - Hoindonjohtopalkkio" - Kaivetaan siis oikea tehtäväryhmä listasta
        tehtavaryhmat (map #(assoc % :id (:tehtavaryhma %) :nimi (:tehtavaryhma_nimi %))
              @tiedot/valitun-urakan-tehtavaryhmat)
        ;; Etsitään G - Hoidonjohtopalkkio tehtäväryhmä, jos se löytyy tehtäväryhmistä
        hoidonjohtopalkkio-tr (some #(when (= "G - Hoidonjohtopalkkio" (:tehtavaryhma_nimi %)) %) tehtavaryhmat)
        tyyppi-valinnat (vec (tiedot/valitun-urakan-sanktiotyypit (:laji @muokattu)))
        aktiivinen-tyyppi (hae-sanktiotyyppi-idlla tyyppi-valinnat (get-in @muokattu [:tyyppi :id]))
        laskutus-ilman-laskutuskelpoisuutta? (= :laskutus_ilman_laskutuskelpoisuutta (:laji @muokattu))
        lomakekonteksti (sanktio-domain/sanktion-lomakekonteksti
                           {:sanktio @muokattu
                            :aktiivinen-tyyppi aktiivinen-tyyppi
                            :yllapitourakka? yllapitourakka?
                            :yllapitokohdeurakka? yllapitokohdeurakka?
                            :vesivaylaurakka? vesivaylaurakka?
                            :laskutus-ilman-laskutuskelpoisuutta? laskutus-ilman-laskutuskelpoisuutta?
                            :aikakausikonteksti aikakausikonteksti
                            :urakan-loppupvm urakan-loppupvm})
        summakonteksti (sanktio-domain/sanktion-summakonteksti
                         {:laji (:laji @muokattu)
                          :omailmoitettu (:omailmoitettu @muokattu)
                          :aktiivinen-tyyppi aktiivinen-tyyppi})
        c-ryhman-maaritys (:c-ryhman-maaritys summakonteksti)
        kiintea-profiilisumma? (:kiintea-profiilisumma? summakonteksti)
        omailmoitus-sallittu? (:omailmoitus-sallittu? summakonteksti)
        kiintea-summamalli (:kiintea-summamalli summakonteksti)
        laskettu-maaritys (:laskettu-maaritys summakonteksti)
        laskentaparametrit (:laskentaparametrit laskettu-maaritys)
        manuaalinen-laskettavan-sisartyyppi? (sanktion-laskenta/manuaalinen-laskettavan-sisartyyppi?
                                               aktiivinen-tyyppi tyyppi-valinnat)
        tallennettava-sanktio (sanktio-domain/sanktion-tallennusmuoto
                                @muokattu summakonteksti
                                (lomake/ilman-lomaketietoja @muokattu))
        ;; Laatupoikkeaman näkymässä sanktio tallennetaan paikallisesti atomiin, ei kantaan.
        tallenna! (if tallenna-fn
                    (fn []
                      (tallenna-fn tallennettava-sanktio)
                      (reset! sivupaneeli-auki?-atom false)
                      (reset! tiedot/valittu-sanktio nil))
                    ;; Suorasanktio tallennetaan suoraan kantaan.
                    (fn []
                      (tiedot/tallenna-sanktio
                        tallennettava-sanktio
                        urakka-id
                        #(reset! sivupaneeli-auki?-atom false))))
        tallennusnapin-komponentti (if tallenna-fn
                                     napit/yleinen-ensisijainen
                                     napit/palvelinkutsu-nappi)
        ;; Lukutila välitetään laatupoikkeaman sanktiolle sanktion tiedoissa.
        lukutila? (if (and (not suorasanktio?) (:lukutila? @muokattu))
                    (:lukutila? @muokattu)
                    lukutila?)
        talvisuolan-validointi-fn (fn [arvo _sanktio]
                                    ((:talvisuolan-validointi lomakekonteksti) arvo))
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
                       [tallennusnapin-komponentti
                        (str "Tallenna" (when muokataan-vanhaa? " muutokset"))
                        tallenna!
                        (cond-> {:ikoni (ikonit/tallenna)
                                 :disabled (or (not voi-muokata?)
                                               (not (lomake/voi-tallentaa? sanktio)))}
                          (not tallenna-fn)
                          (assoc :luokka "nappi-ensisijainen"))])
                     (when (and voi-muokata? saa-poistaa? (or (:id @muokattu) (:lukutila? @muokattu)) (not lukutila?))
                       [:button.nappi-kielteinen.oikealle
                        {:on-click
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
      (into []
        (concat
          (sanktion-laji-ja-tyyppikentat
            @muokattu
            lukutila?
            mahdolliset-sanktiolajit
            tyyppi-valinnat
            urakan-alkuvuosi
            @tiedot-urakka/urakan-toimenpideinstanssit
            lomakekonteksti)
          (mapcat #(if (vector? %) % [%])
            [(yllapitokohde-kentta yllapitokohdeurakka? yllapitokohteet voi-muokata?)

             (tapahtumapaikka-kentta
               lomakekonteksti
               voi-muokata?)

             (puute-tai-laiminlyonti-kentta lomakekonteksti)

             (perustelu-kentta lomakekonteksti voi-muokata?)

             (omailmoitus-kentta omailmoitus-sallittu?)

             (summan-maarityksen-kentat
               laskutus-ilman-laskutuskelpoisuutta?
               laskettu-maaritys
               laskentaparametrit
               manuaalinen-laskettavan-sisartyyppi?
               c-ryhman-maaritys
               kiintea-profiilisumma?
               (:summa kiintea-summamalli)
               aikakausikonteksti
               @muokattu
               lukutila?
               (tiedot-urakka/indeksi-kaytossa-sakoissa?)
               (:indeksi @nav/valittu-urakka))

             (kulun-kohdistus-kentta
               @muokattu
               aikakausikonteksti
               lukutila?
               mahdolliset-kulun-kohdistukset
               hoidonjohtopalkkio-tr
               tiedot-urakka/urakan-toimenpideinstanssit)

             (paivamaarat-ja-perinta-kentat
               laskutus-ilman-laskutuskelpoisuutta?
               aikakausikonteksti
               voi-muokata?
               lukutila?
               laskutuskuukausi-id
               laskutuskuukaudet
               yllapitourakka?
               hoitovuodet
               perintapvm->hoitovuosi
               hoitovuosi->teksti
               talvisuolan-validointi-fn)

             (kasittely-kentat aikakausikonteksti lukutila? voi-muokata? @muokattu)

             (liitteet-muokkaustilassa-kentta lukutila? urakka-id liitteet-id muokattu)

             (liitteet-lukutilassa-kentta lukutila? suorasanktio? muokattu)

             (kirjaaja-kentta lukutila?)])))
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
