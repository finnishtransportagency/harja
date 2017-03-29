(ns harja.ui.kartta.infopaneelin-sisalto
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartan
  infopaneeliin.
  Infopaneelissa voidaan näyttää erityyppisiä asioita, samalla tavalla kuin kartalle
  piirtämisessäkin. Jokaiselle eri tyyppiselle asialle täytyy määritellä toteutus
  infopaneli-skeema multimetodiin.

  Infopaneeli-skeema palauttaa mäp muotoisen kuvauksen näytettävästä asiasta, jossa
  on seuraavat avaimet:

  :tyyppi       näytettävän asian tyyppi keyword
  :jarjesta-fn  funktio joka palauttaa datasta sort avaimen (pvm)
  :otsikko      asialle näytettävä otsikko
  :tiedot       asialle näytettävien tietokenttien skeemat
  :data         itse näytettävä asia, josta tietokenttien arvot saadaan

  Infopaneelin skeemat ovat hyvin samankaltaisia lomakkeen skeemojen kanssa. Voisimme aivan hyvin
  ottaa jokaisen rivin arvon datasta suoraan, esim {:otsikko 'Alkanut' :arvo (:alkanut data)}, mutta
  lähinnä yhtenäisyyden vuoksi noudatamme täällä samaa tyyliä, kuin lomakkeen skeemoissa, eli määrittelemme
  skeemassa vain funktiot, millä data haetaan.

  :hae avaimen alla pitää skeemasta löytyä {:validointi-fn (..) :haku-fn (..)}. :validointi-fn
  käytetään skeeman validointivaiheessa varmistamaan, että annetussa skeemassa on kaikki tarvittavat
  tiedot saatavilla. Lopuksi :validointi-fn poistetaan, ja :hae avaimen alle asetetaan :haku-fn'n arvo.

  Jos arvoa, jonka mukaan asia halutaan järjestää, ei ole, pitäisi :jarjesta-fn arvon olla
  funktio, joka palauttaa aina falsen, eli (constantly false). Tässä tapauksessa false tulkitaan siten,
  että arvon puuttuminen on tiedostettu tilanne, eikä testit epäonnistu.

  Ainoa syy, miksi tämä on .cljc tiedosto, on testattavuus. Näin voimme kirjoittaa testejä,
  jossa haetaan palvelimelta payload, jolle yritetään luoda onnistuneesti skeema."
  (:require [clojure.string :as string]
            [harja.pvm :as pvm]
    #?(:cljs [harja.loki :as log :refer [log warn]])
    #?(:cljs [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :refer [kuvaile-paatostyyppi]])
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
            [harja.domain.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.tietyoilmoitukset :as t-domain]
            [harja.fmt :as fmt]
            [harja.domain.tierekisteri.varusteet :as varusteet]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

#?(:clj
   (defn kuvaile-paatostyyppi [_] "Tämä on olemassa vain testejä varten"))

#?(:clj
   (def warn println))

#?(:clj
   (def log println))

#?(:clj
   (def clj->js identity))

(defn- sisaltaa? [data path]
  (let [puuttuu ::avain-puuttuu]
    (not= puuttuu
          (if (keyword? path)
            (get data path puuttuu)
            (get-in data path puuttuu)))))

(defn- hakufunktio
  [validointi-fn-tai-vaaditut-avaimet haku-fn]
  {:validointi-fn (cond
                    (set? validointi-fn-tai-vaaditut-avaimet)
                    #(every? true? (map (fn [avain] (sisaltaa? % avain)) validointi-fn-tai-vaaditut-avaimet))

                    (keyword? validointi-fn-tai-vaaditut-avaimet)
                    #(contains? % validointi-fn-tai-vaaditut-avaimet)

                    :default validointi-fn-tai-vaaditut-avaimet)
   :haku-fn haku-fn})

(defmethod infopaneeli-skeema :tyokone [tyokone]
  {:tyyppi :tyokone
   :jarjesta-fn :viimeisin-havainto
   :otsikko (str (pvm/pvm-aika (:viimeisin-havainto tyokone)) " - Työkone: "
                 (when (:tehtavat tyokone)
                   (string/join ", " (:tehtavat tyokone))))
   :tiedot [{:otsikko "Ensimmäinen havainto" :tyyppi :pvm-aika :nimi :ensimmainen-havainto}
            {:otsikko "Viimeisin havainto" :tyyppi :pvm-aika :nimi :viimeisin-havainto}
            {:otsikko "Tyyppi" :tyyppi :string :nimi :tyokonetyyppi}
            {:otsikko "Organisaatio" :tyyppi :string :hae (hakufunktio :organisaationimi #(or (:organisaationimi %) "Ei organisaatiotietoja"))}
            {:otsikko "Urakka" :tyyppi :string :hae (hakufunktio :urakkanimi #(or (:urakkanimi %) "Ei urakkatietoja"))}
            {:otsikko "Tehtävät" :tyyppi :string
             :hae (hakufunktio :tehtavat #(string/join ", " (:tehtavat %)))}]
   :data tyokone})

(defn- ilmoituksen-tiedot [ilmoitus]
  {:tyyppi :ilmoitus
   :jarjesta-fn :ilmoitettu
   :otsikko (str
              (pvm/pvm-aika (:ilmoitettu ilmoitus)) " - "
              (condp = (:ilmoitustyyppi ilmoitus)
                :toimenpidepyynto "Toimenpidepyyntö"
                :tiedoitus "Tiedotus"
                (string/capitalize (name (:ilmoitustyyppi ilmoitus)))))
   :tiedot [{:otsikko "Id" :tyyppi :string :nimi :ilmoitusid}
            {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
            {:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
            {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
            {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
            {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
            {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero :kokonaisluku? true
             :hae (hakufunktio :kuittaukset #(count (:kuittaukset %)))}]
   :data ilmoitus})

(defmethod infopaneeli-skeema :toimenpidepyynto [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :tiedoitus [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :kysely [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))


(defmethod infopaneeli-skeema :varustetoteuma [toteuma]
  {:tyyppi :varustetoteuma
   :jarjesta-fn :alkupvm
   :otsikko (str (pvm/pvm-aika (:alkupvm toteuma)) " - Varustetoteuma")
   :tiedot [{:otsikko "Päivämäärä" :tyyppi :pvm :nimi :alkupvm}
            {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
            {:otsikko "Tietolaji" :tyyppi :string :nimi :tietolaji}
            {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide
             :hae (hakufunktio :toimenpide #(varusteet/varuste-toimenpide->string (:toimenpide %)))}
            {:otsikko "Kuntoluokka" :tyyppi :string :nimi :kuntoluokka}]
   :data toteuma})

(defn- yllapitokohde-skeema
  "Ottaa ylläpitokohdeosan, jolla on lisäksi tietoa sen 'pääkohteesta' :yllapitokohde avaimen takana."
  [yllapitokohdeosa]
  (let [kohde-aloitus :kohde-alkupvm
        paallystys-aloitus :paallystys-alkupvm
        paallystys-valmis :paallystys-loppupvm
        paikkaus-aloitus :paikkaus-alkupvm
        paikkaus-valmis :paikkaus-loppupvm
        tiemerkinta-aloitus :tiemerkinta-alkupvm
        tiemerkinta-valmis :tiemerkinta-loppupvm
        kohde-valmis :kohde-valmispvm
        aikataulu-teksti (fn [pvm otsikko pvm-tyyppi]
                           (if (and pvm (pvm/sama-tai-ennen? pvm (pvm/nyt)))
                             (str otsikko " " (case pvm-tyyppi
                                                    :aloitus "aloitettu"
                                                    :valmistuminen "valmistunut"))
                             (str otsikko " " (case pvm-tyyppi
                                                    :aloitus "aloitetaan"
                                                    :valmistuminen "valmistuu"))))
        kohde-aloitus-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde kohde-aloitus])
                                               "Kohde" :aloitus)
        paallystys-aloitus-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde paallystys-aloitus])
                                                    "Päällystys" :aloitus)
        paallystys-valmis-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde paallystys-valmis])
                                                   "Päällystys" :valmistuminen)
        paikkaus-aloitus-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde paikkaus-aloitus])
                                                  "Paikkaus" :aloitus)
        paikkaus-valmis-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde paikkaus-valmis])
                                                 "Paikkaus" :valmistuminen)
        tiemerkinta-aloitus-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-aloitus])
                                                     "Tiemerkintä" :aloitus)
        tiemerkinta-valmis-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-valmis])
                                                    "Tiemerkintä" :valmistuminen)
        kohde-valmis-teksti (aikataulu-teksti (get-in yllapitokohdeosa [:yllapitokohde kohde-valmis])
                                              "Kohde" :valmistuminen)]
    {:tyyppi (:yllapitokohdetyotyyppi (:yllapitokohde yllapitokohdeosa))
     :jarjesta-fn (let [fn #(get-in % [:yllapitokohde kohde-aloitus])]
                    (if (fn yllapitokohdeosa)
                      fn
                      ;; Ylläpitokohteella ei ole välttämättä alkupäivämäärää.
                      ;; Tällöin haluamme järjestää kohteen listan alimmaiseksi.
                      ;; Emme voi sallia, että jarjesta-fn palauttaa nil, koska
                      ;; tällöin skeeman validointi ei mene läpi - validointi luulee,
                      ;; että yritetään käyttää avainta, joka on unohtunut palauttaa
                      ;; palvelimelta.
                      (constantly false)))
     :otsikko (case (:yllapitokohdetyotyyppi (:yllapitokohde yllapitokohdeosa))
                :paallystys "Päällystyskohde"
                :paikkaus "Paikkauskohde"
                nil)
     :tiedot [{:otsikko "Kohde" :tyyppi :string :hae (hakufunktio #{[:yllapitokohde :nimi]}
                                                                  #(get-in % [:yllapitokohde :nimi]))}
              {:otsikko "Kohdenumero" :tyyppi :string :hae (hakufunktio #{[:yllapitokohde :kohdenumero]}
                                                                        #(get-in % [:yllapitokohde :kohdenumero]))}
              {:otsikko "Kohteen osoite" :tyyppi :string
               :hae (hakufunktio :yllapitokohde #(tr-domain/tierekisteriosoite-tekstina (:yllapitokohde %)))}
              {:otsikko "Kohteen pituus (m)" :tyyppi :string
               :hae (hakufunktio #{[:yllapitokohde :pituus]} #(fmt/desimaaliluku-opt (get-in % [:yllapitokohde :pituus]) 0))}
              {:otsikko "Alikohde" :tyyppi :string :nimi :nimi}
              {:otsikko "Alikohteen osoite" :tyyppi :string
               :hae (hakufunktio
                      #(some true? (map (partial contains? %) [:numero :tr-numero :tie]))
                      #(tr-domain/tierekisteriosoite-tekstina %))}
              {:otsikko "Nykyinen päällyste" :tyyppi :string
               :hae (hakufunktio #{[:yllapitokohde :nykyinen-paallyste]}
                                 #(paallystys-ja-paikkaus/hae-paallyste-koodilla (get-in % [:yllapitokohde :nykyinen-paallyste])))}
              {:otsikko "KVL" :tyyppi :string
               :hae (hakufunktio
                      #{[:yllapitokohde :keskimaarainen-vuorokausiliikenne]}
                      #(fmt/desimaaliluku-opt (get-in % [:yllapitokohde :keskimaarainen-vuorokausiliikenne]) 0))}
              {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
              {:otsikko "Tila" :tyyppi :string
               :hae (hakufunktio
                      #{[:yllapitokohde :tila]}
                      #(yllapitokohteet-domain/kuvaile-kohteen-tila (get-in % [:yllapitokohde :tila])))}
              ;; Aikataulutiedot
              (when (get-in yllapitokohdeosa [:yllapitokohde kohde-aloitus])
                {:otsikko kohde-aloitus-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde kohde-aloitus]}
                        #(get-in % [:yllapitokohde kohde-aloitus]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde paallystys-aloitus])
                {:otsikko paallystys-aloitus-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde paallystys-aloitus]}
                        #(get-in % [:yllapitokohde paallystys-aloitus]))})
              (when (get-in yllapitokohdeosa [:yllapitokohde paallystys-valmis])
                {:otsikko paallystys-valmis-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde paallystys-valmis]}
                        #(get-in % [:yllapitokohde paallystys-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde paikkaus-aloitus])
                {:otsikko paikkaus-aloitus-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde paikkaus-aloitus]}
                        #(get-in % [:yllapitokohde paikkaus-aloitus]))})
              (when (get-in yllapitokohdeosa [:yllapitokohde paikkaus-valmis])
                {:otsikko paikkaus-valmis-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde paikkaus-valmis]}
                        #(get-in % [:yllapitokohde paikkaus-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-aloitus])
                {:otsikko tiemerkinta-aloitus-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde tiemerkinta-aloitus]}
                        #(get-in % [:yllapitokohde tiemerkinta-aloitus]))})
              (when (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-valmis])
                {:otsikko tiemerkinta-valmis-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde tiemerkinta-valmis]}
                        #(get-in % [:yllapitokohde tiemerkinta-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde kohde-valmis])
                {:otsikko kohde-valmis-teksti :tyyppi :pvm-aika
                 :hae (hakufunktio
                        #{[:yllapitokohde kohde-valmis]}
                        #(get-in % [:yllapitokohde kohde-valmis]))})
              ;; Muut
              {:otsikko "Urakka" :tyyppi :string :hae (hakufunktio
                                                        #{[:yllapitokohde :urakka]}
                                                        #(get-in % [:yllapitokohde :urakka]))}
              {:otsikko "Urakoitsija" :tyyppi :string :hae (hakufunktio
                                                             #{[:yllapitokohde :urakoitsija]}
                                                             #(get-in % [:yllapitokohde :urakoitsija]))}]
     :data yllapitokohdeosa}))

(defmethod infopaneeli-skeema :paallystys [paallystys]
  (yllapitokohde-skeema paallystys))

(defmethod infopaneeli-skeema :paikkaus [paikkaus]
  (yllapitokohde-skeema paikkaus))

(defmethod infopaneeli-skeema :turvallisuuspoikkeama [turpo]
  (let [tapahtunut :tapahtunut
        paattynyt :paattynyt
        kasitelty :kasitelty]
    {:tyyppi :turvallisuuspoikkeama
     :jarjesta-fn :tapahtunut
     :otsikko (str (pvm/pvm-aika (tapahtunut turpo)) " - Turvallisuuspoikkeama")
     :tiedot [(when (tapahtunut turpo)
                {:otsikko "Tapahtunut" :tyyppi :pvm-aika :nimi tapahtunut})
              (when (kasitelty turpo)
                {:otsikko "Käsitelty" :tyyppi :pvm-aika :nimi kasitelty})
              {:otsikko "Työn\u00ADtekijä" :hae (hakufunktio
                                                  #(or (contains? % :tyontekijanammatti)
                                                       (contains? % :tyontekijanammattimuu))
                                                  #(turpodomain/kuvaile-tyontekijan-ammatti %))}
              {:otsikko "Vammat" :hae (hakufunktio
                                        :vammat
                                        #(or (turpodomain/vammat (:vammat %)) ""))}
              {:otsikko "Sairaala\u00ADvuorokaudet" :nimi :sairaalavuorokaudet}
              {:otsikko "Sairaus\u00ADpoissaolo\u00ADpäivät" :tyyppi :positiivinen-numero :nimi :sairauspoissaolopaivat}
              {:otsikko "Vakavuus\u00ADaste" :hae (hakufunktio
                                                    :vakavuusaste
                                                    #(turpodomain/turpo-vakavuusasteet (:vakavuusaste %)))}
              {:otsikko "Kuvaus" :nimi :kuvaus}
              {:otsikko "Korjaavat toimen\u00ADpiteet"
               :hae (hakufunktio
                      :korjaavattoimenpiteet
                      #(str (count (filter :suoritettu (:korjaavattoimenpiteet %)))
                            "/"
                            (count (:korjaavattoimenpiteet %))))}]
     :data turpo}))

(defmethod infopaneeli-skeema :tarkastus [tarkastus]
  (let [havainnot-fn #(cond
                        (and (:havainnot %) (not-empty (:vakiohavainnot %)))
                        (str (:havainnot %) " & " (string/join ", " (:vakiohavainnot %)))

                        (:havainnot %)
                        (:havainnot %)

                        (not-empty (:vakiohavainnot %))
                        (string/join ", " (:vakiohavainnot %))

                        :default nil)]
    {:tyyppi :tarkastus
     :jarjesta-fn :aika
     :otsikko (str (pvm/pvm-aika (:aika tarkastus)) " - " (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)))
     :tiedot [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
              {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite :nimi :tierekisteriosoite}
              {:otsikko "Tarkastaja" :nimi :tarkastaja}
              {:otsikko "Havainnot" :hae (hakufunktio
                                           #(or (contains? % :havainnot)
                                                (contains? % :vakiohavainnot))
                                           havainnot-fn)}]
     :data tarkastus}))

(defmethod infopaneeli-skeema :laatupoikkeama [laatupoikkeama]
  (let [paatos #(get-in % [:paatos :paatos])
        kasittelyaika #(get-in % [:paatos :kasittelyaika])]
    {:tyyppi :laatupoikkeama
     :jarjesta-fn :aika
     :otsikko (str (pvm/pvm-aika (:aika laatupoikkeama)) " - Laatupoikkeama")
     :tiedot [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
              {:otsikko "Tekijä" :hae (hakufunktio
                                        #{:tekijanimi :tekija}
                                        #(str (:tekijanimi %) ", " (name (:tekija %))))}
              {:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :string}
              {:otsikko "Tierekisteriosoite" :hae (hakufunktio
                                                    #(or (sisaltaa? % [:yllapitokohde :tr])
                                                         (sisaltaa? % :tr))
                                                    #(if-let [yllapitokohde-tie (get-in % [:yllapitokohde :tr])]
                                                       (tr-domain/tierekisteriosoite-tekstina
                                                         yllapitokohde-tie)
                                                       (tr-domain/tierekisteriosoite-tekstina
                                                         (:tr %))))}
              (when (:yllapitokohde laatupoikkeama)
                {:otsikko "Kohde" :hae (hakufunktio
                                         #{[:yllapitokohde :numero] [:yllapitokohde :nimi]}
                                         #(let [yllapitokohde (:yllapitokohde %)]
                                            (str (:numero yllapitokohde)
                                                 ", "
                                                 (:nimi yllapitokohde))))})
              (when (and (paatos laatupoikkeama) (kasittelyaika laatupoikkeama))
                {:otsikko "Päätös"
                 :hae (hakufunktio
                        #{[:paatos :paatos] [:paatos :kasittelyaika]}
                        #(str (kuvaile-paatostyyppi (paatos %))
                              " (" (pvm/pvm-aika (kasittelyaika %)) ")"))})]
     :data laatupoikkeama}))

(defmethod infopaneeli-skeema :suljettu-tieosuus [osuus]
  {:tyyppi :suljettu-tieosuus
   :jarjesta-fn :aika
   :otsikko "Suljettu tieosuus"
   :tiedot [{:otsikko "Ylläpitokohde" :hae (hakufunktio
                                             #{:yllapitokohteen-nimi :yllapitokohteen-numero}
                                             #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")"))}
            {:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
            {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
            {:otsikko "Kaistat" :hae (hakufunktio
                                       :kaistat
                                       #(string/join ", " (map str (:kaistat %))))}
            {:otsikko "Ajoradat" :hae (hakufunktio
                                        :ajoradat
                                        #(string/join ", " (map str (:ajoradat %))))}]
   :data osuus})

(defmethod infopaneeli-skeema :toteuma [toteuma]
  {:tyyppi :toteuma
   :jarjesta-fn :alkanut
   :otsikko (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))]
              (str (pvm/pvm-aika (:alkanut toteuma)) " - "
                   (if (empty? toimenpiteet)
                     "Toteuma"
                     (string/join ", " toimenpiteet))))
   :tiedot (vec (concat [{:otsikko "Alkanut" :tyyppi :pvm-aika :nimi :alkanut}
                         {:otsikko "Päättynyt" :tyyppi :pvm-aika :nimi :paattynyt}
                         {:otsikko "Klo (arvio)" :tyyppi :pvm-aika
                          ;; Arvioitu aika pisteessä saa puuttua, eli validointifunktio on (constantly true)
                          :hae (hakufunktio (constantly true) :aika-pisteessa)}
                         {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite
                          :nimi :tierekisteriosoite}
                         {:otsikko "Suorittaja" :hae (hakufunktio
                                                       #{[:suorittaja :nimi]}
                                                       #(get-in % [:suorittaja :nimi]))}]

                        (for [{:keys [toimenpide maara yksikko]} (:tehtavat toteuma)]
                          {:otsikko toimenpide
                           :hae (hakufunktio
                                  ;; Näitä ei edes tehdä jos arvot puuttuvat, joten ei
                                  ;; tarvita erityistä validointia.
                                  (constantly true)
                                  (constantly (str maara " " yksikko)))})

                        (for [materiaalitoteuma (:materiaalit toteuma)]
                          {:otsikko (get-in materiaalitoteuma [:materiaali :nimi])
                           :hae (hakufunktio
                                  (constantly true)
                                  #(str (get-in % [:materiaalit materiaalitoteuma :maara]) " "
                                        (get-in % [:materiaalit materiaalitoteuma :materiaali :yksikko])))})
                        (when (:lisatieto toteuma)
                          [{:otsikko "Lisätieto" :nimi :lisatieto}])))
   :data toteuma})

(defmethod infopaneeli-skeema :tietyoilmoitus [tietyoilmoitus]
  {:tyyppi :tietyoilmoitus
   :jarjesta-fn ::t-domain/alku
   :otsikko "Tietyöilmoitus"
   :tiedot [{:otsikko "Ilmoittaja" :hae (hakufunktio
                                          #{[::t-domain/ilmoittaja ::t-domain/etunimi]}
                                          #(str (get-in % [::t-domain/ilmoittaja ::t-domain/etunimi])))}]
   :data tietyoilmoitus})

(defmethod infopaneeli-skeema :silta [silta]
  {:tyyppi :silta
   :jarjesta-fn (let [fn :tarkastusaika]
                  (if (fn silta)
                    fn
                    (constantly false)))
   :otsikko (or (:siltanimi silta) "Silta")
   :tiedot [{:otsikko "Nimi" :nimi :siltanimi}
            {:otsikko "Sillan tunnus" :nimi :siltatunnus}
            {:otsikko "Edellinen tarkastus" :tyyppi :pvm :nimi :tarkastusaika}
            {:otsikko "Edellinen tarkastaja" :nimi :tarkastaja}]
   :data silta})

(defmethod infopaneeli-skeema :tietyomaa [tietyomaa]
  {:tyyppi :tietyomaa
   :jarjesta-fn :aika
   :otsikko (str (when (:aika tietyomaa)
                   (str (pvm/pvm-aika (:aika tietyomaa)) " - ")) "Tietyömaa")
   :tiedot [{:otsikko "Ylläpitokohde" :hae (hakufunktio
                                             #{:yllapitokohteen-nimi :yllapitokohteen-numero}
                                             #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")"))}
            {:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
            {:otsikko "Osoite" :hae (hakufunktio
                                      #(some true? (map (partial contains? %) [:numero :tr-numero :tie]))
                                      #(tr-domain/tierekisteriosoite-tekstina % {:teksti-tie? false}))}
            {:otsikko "Kaistat" :hae (hakufunktio
                                       :kaistat
                                       #(clojure.string/join ", " (map str (:kaistat %))))}
            {:otsikko "Ajoradat" :hae (hakufunktio
                                        :ajoradat
                                        #(clojure.string/join ", " (map str (:ajoradat %))))}
            {:otsikko "Nopeusrajoitus" :nimi :nopeusrajoitus}]
   :data tietyomaa})

(defmethod infopaneeli-skeema :default [x]
  (warn "infopaneeli-skeema metodia ei implementoitu tyypille " (pr-str (:tyyppi-kartalla x))
        ", palautetaan tyhjä itemille " (pr-str x))
  nil)

(defn- rivin-skeemavirhe [viesti rivin-skeema infopaneeli-skeema]
  (do
    (log viesti
         ", rivin-skeema: " (pr-str rivin-skeema)
         ", infopaneeli-skeema: " (pr-str infopaneeli-skeema))
    nil))

(defn- rivin-skeema-ilman-haun-validointia [skeema]
  (if-let [hae (:hae skeema)]
    (assoc skeema :hae (:haku-fn hae))

    skeema))

(defn- validoi-rivin-skeema
  "Validoi rivin skeeman annetulle infopaneeli skeemalle. Palauttaa skeeman, jos se on validi.
  Jos skeema ei ole validi tiedolle, logittaa virheen ja palauttaa nil."
  [infopaneeli-skeema {:keys [nimi hae otsikko] :as rivin-skeema}]
  (let [data (:data infopaneeli-skeema)
        get-fn (or nimi (:haku-fn hae))]
    (cond
      ;; Ei ole otsikkoa
      (nil? otsikko)
      (rivin-skeemavirhe "Rivin skeemasta puuttuu otsikko"
                         rivin-skeema infopaneeli-skeema)

      ;; Hakutapa puuttuu kokonaan
      (nil? get-fn)
      (rivin-skeemavirhe (str otsikko " skeemasta puuttuu :nimi tai :hae")
                         rivin-skeema infopaneeli-skeema)

      ;; Hakutapa on nimi, mutta datassa ei ole kyseistä avainta
      (and nimi (not (contains? data nimi)))
      (rivin-skeemavirhe
        (str otsikko " tiedossa ei ole nimen mukaista avainta, nimi: "
             (str nimi))
        rivin-skeema infopaneeli-skeema)

      ;; Hakutapa on funktio, jolta puuttuu validointi
      (and hae (nil? (:validointi-fn hae)))
      (rivin-skeemavirhe (str otsikko " :hae avaimen pitää sisältää map, jossa on :haku-fn ja :validointi-fn. " (:otsikko rivin-skeema))
                         rivin-skeema infopaneeli-skeema)

      ;; Haun validointi epäonnistuu
      (and hae (not ((:validointi-fn hae) data)))
      (rivin-skeemavirhe (str otsikko " :hae kentän :validointi-fn epäonnistui. Puuttuuko avaimia?")
                         rivin-skeema infopaneeli-skeema)

      ;; Kaikki kunnossa
      :default
      (rivin-skeema-ilman-haun-validointia rivin-skeema))))

(defn- validoi-infopaneeli-skeema
  "Validoi infopaneeli-skeema metodin muodostaman skeeman ja kaikki sen kentät.
  Jos skeema on validi, palauttaa skeeman sen valideilla kentillä.
  Jos skeema ei ole validi, logittaa virheen ja palauttaa nil."
  ([skeema] (validoi-infopaneeli-skeema skeema false))
  ([{:keys [otsikko tiedot data jarjesta-fn] :as infopaneeli-skeema} vaadi-kaikki-skeemat?]
   (let [validoidut-skeemat (map (partial validoi-rivin-skeema infopaneeli-skeema)
                                 tiedot)
         validit-skeemat (vec (keep identity validoidut-skeemat))
         epaonnistuneet-skeemat-lkm (count (filter nil? validoidut-skeemat))]
     (cond
       (nil? otsikko)
       (do (warn (str "Otsikko puuttuu " (pr-str infopaneeli-skeema)))
           nil)

       (nil? jarjesta-fn)
       (do (warn (str "jarjesta-fn puuttuu tiedolta " (pr-str infopaneeli-skeema)))
           nil)

       (empty? validit-skeemat)
       (do (warn (str "Tiedolla ei ole yhtään validia skeemaa: " (pr-str infopaneeli-skeema)))
           nil)

       (and vaadi-kaikki-skeemat? (pos? epaonnistuneet-skeemat-lkm))
       (do
         (warn
           (str
             epaonnistuneet-skeemat-lkm
             " puutteellista skeemaa, kun yksikään ei saisi epäonnistua "
             (pr-str data)))
         nil)

       ;; Järjestäminen yritetään tehdä avaimella, jota ei datasta löydy
       (nil? (jarjesta-fn data))
       (do
         (warn (str "jarjesta-fn on määritelty, mutta avaimella ei löydy dataa skeemasta " (pr-str infopaneeli-skeema)))
         nil)

       :default
       (assoc infopaneeli-skeema :tiedot validit-skeemat)))))

(defn- jarjesta [eka toka]
  (cond
    (false? eka)
    false

    (false? toka)
    true

    :default (pvm/jalkeen? eka toka)))

(defn- skeema-ilman-tyhjia-riveja [skeema]
  (assoc skeema :tiedot (keep identity (:tiedot skeema))))

(defn skeemamuodossa
  ([asiat]
   (as-> asiat $
         (keep infopaneeli-skeema $)
         (map skeema-ilman-tyhjia-riveja $)
         (keep validoi-infopaneeli-skeema $)
         (sort-by #((:jarjesta-fn %) (:data %)) jarjesta $)
         (vec $))))
