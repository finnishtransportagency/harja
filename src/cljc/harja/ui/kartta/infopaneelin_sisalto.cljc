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
            [taoensso.timbre :as log]
    #?(:cljs [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :refer [kuvaile-paatostyyppi]])
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.turvallisuuspoikkeama :as turpodomain]
            [harja.domain.laadunseuranta.tarkastus :as tarkastukset]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.tietyoilmoitus :as t-domain]
            [harja.domain.tieliikenneilmoitukset :as apurit]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.vayla :as v]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.domain.kayttaja :as kayttaja]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.kanavat.kanavan-toimenpide :as kan-to]
            [harja.domain.kanavat.hairiotilanne :as ht]
            [harja.domain.kanavat.liikennetapahtuma :as liikenne]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tielupa :as tielupa]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

#?(:clj
   (defn kuvaile-paatostyyppi [_] "Tämä on olemassa vain testejä varten"))

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
  (let [nayta-max-kuittausta 10]
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
              {:otsikko "Tiedotettu HARJAan" :tyyppi :pvm-aika :nimi :valitetty}
              {:otsikko "Tiedotettu urakkaan" :tyyppi :pvm-aika :nimi :valitetty-urakkaan}
              {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
              {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
              {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
              {:otsikko "Kuittaukset" :nimi :kuittaukset :tyyppi :komponentti
               :komponentti
               (fn []
                 (let [kuittaukset (filterv #(not= :valitys (:kuittaustyyppi %)) (:kuittaukset ilmoitus))
                       kuittauksien-maara (count kuittaukset)
                       kaikkia-ei-piirretty? (> kuittauksien-maara nayta-max-kuittausta)]
                   [:div
                    (for [ilmoitus (take nayta-max-kuittausta (sort-by :kuitattu pvm/jalkeen? kuittaukset))]
                      ^{:key (:id ilmoitus)}
                      [:div.tietorivi.tietorivi-ilman-alaviivaa
                       [:span.tietokentta {:style {:display "auto"
                                                   :font-weight "normal"}}
                        (str (if (keyword? (:kuittaustyyppi ilmoitus))
                               (-> ilmoitus :kuittaustyyppi apurit/kuittaustyypin-otsikko)
                               "-"))]
                       [:span.tietoarvo (pvm/pvm (:kuitattu ilmoitus))]])
                    (when kaikkia-ei-piirretty?
                      [:div (str "...sekä "
                                 (- kuittauksien-maara nayta-max-kuittausta)
                                 " muuta toimenpidettä.")])
                    ]))}]
     :data ilmoitus}))

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

(defmethod infopaneeli-skeema :varuste [varuste]
  (let [tietolaji (get-in varuste [:varuste :tietue :tietolaji :tunniste])]
    {:tyyppi :varuste
     :jarjesta-fn (constantly (pvm/nyt))
     :otsikko "Varuste"
     :tiedot [{:otsikko "Tietolaji" :tyyppi :string :nimi :tietolaji}
              {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
              {:otsikko "Osoite" :tyyppi :string :nimi :osoite}
              {:otsikko "Kuntoluokitus" :tyyppi :string :nimi :kuntoluokitus}]
     :data {:tunniste (get-in varuste [:varuste :tunniste])
            :tietolaji (str (varusteet/tietolaji->selitys tietolaji) " (" tietolaji ")")
            :tietolajin-tunniste tietolaji
            :tie (get-in varuste [:varuste :tietue :sijainti :tie])
            :osoite (tr-domain/tierekisteriosoite-tekstina (get-in varuste [:varuste :tietue :sijainti :tie]))
            :kuntoluokitus (get-in varuste [:varuste :tietue :kuntoluokitus])}}))

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
                ;; Näytetään päällystykselle kohteen nimi ja osan TR-osoite
                :paallystys (str (:nimi (:yllapitokohde yllapitokohdeosa))
                                 " (" (tr-domain/tierekisteriosoite-tekstina
                                        yllapitokohdeosa {:teksti-tie? false}) ")")
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
                {:otsikko kohde-aloitus-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde kohde-aloitus]}
                        #(get-in % [:yllapitokohde kohde-aloitus]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde paallystys-aloitus])
                {:otsikko paallystys-aloitus-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde paallystys-aloitus]}
                        #(get-in % [:yllapitokohde paallystys-aloitus]))})
              (when (get-in yllapitokohdeosa [:yllapitokohde paallystys-valmis])
                {:otsikko paallystys-valmis-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde paallystys-valmis]}
                        #(get-in % [:yllapitokohde paallystys-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde paikkaus-aloitus])
                {:otsikko paikkaus-aloitus-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde paikkaus-aloitus]}
                        #(get-in % [:yllapitokohde paikkaus-aloitus]))})
              (when (:loppuaika yllapitokohdeosa)
                {:otsikko "Paikkaus valmistunut" :tyyppi :pvm :nimi :loppuaika})
              (when (:tr-ajorata yllapitokohdeosa)
                {:otsikko "Ajorata" :tyyppi :string :nimi :tr-ajorata})
              (when (get-in yllapitokohdeosa [:yllapitokohde paikkaus-valmis])
                {:otsikko paikkaus-valmis-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde paikkaus-valmis]}
                        #(get-in % [:yllapitokohde paikkaus-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-aloitus])
                {:otsikko tiemerkinta-aloitus-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde tiemerkinta-aloitus]}
                        #(get-in % [:yllapitokohde tiemerkinta-aloitus]))})
              (when (get-in yllapitokohdeosa [:yllapitokohde tiemerkinta-valmis])
                {:otsikko tiemerkinta-valmis-teksti :tyyppi :pvm
                 :hae (hakufunktio
                        #{[:yllapitokohde tiemerkinta-valmis]}
                        #(get-in % [:yllapitokohde tiemerkinta-valmis]))})

              (when (get-in yllapitokohdeosa [:yllapitokohde kohde-valmis])
                {:otsikko kohde-valmis-teksti :tyyppi :pvm
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
  (let [havainnot-fn (fn [t]
                       #?(:clj (constantly nil))
                       #?(:cljs
                          (->> (conj []
                                     (:havainnot t)
                                     (tarkastukset/formatoi-vakiohavainnot (:vakiohavainnot t))
                                     (tarkastukset/formatoi-talvihoitomittaukset (:talvihoitomittaus t))
                                     (tarkastukset/formatoi-soratiemittaukset (:soratiemittaus t)))
                               (remove empty?)
                               (string/join " & "))))]
    {:tyyppi :tarkastus
     :jarjesta-fn :aika
     :otsikko (let [tila (cond
                           (:laadunalitus tarkastus)
                           "laadunalitus"

                           (tarkastukset/luminen-vakiohavainto? tarkastus)
                           "lunta"

                           (tarkastukset/liukas-vakiohavainto? tarkastus)
                           "liukasta"

                           (tarkastukset/tarkastus-sisaltaa-havaintoja? tarkastus)
                           "havaintoja"

                           :else nil)
                    tila-str (when tila (str ", " tila))
                    otsikko-vektori [(str (pvm/pvm-aika (:aika tarkastus))) (str (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)) tila-str)]]
                otsikko-vektori
                )
     :tiedot [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
              {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite :nimi :tierekisteriosoite}
              {:otsikko "Tarkastaja" :nimi :tarkastaja}
              {:otsikko "Havainnot" :hae (hakufunktio
                                           #(and (contains? % :havainnot)
                                                 (contains? % :vakiohavainnot)
                                                 (contains? % :talvihoitomittaus)
                                                 (contains? % :soratiemittaus))
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
              (when (and (get-in laatupoikkeama [:yllapitokohde :numero])
                         (get-in laatupoikkeama [:yllapitokohde :nimi]))
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
                                                       #(get-in % [:suorittaja :nimi]))}
                         (when (:tyokonetyyppi toteuma)
                           {:otsikko "Työkonetyyppi" :nimi :tyokonetyyppi})
                         (when (:tyokonelisatieto toteuma)
                           {:otsikko "Työkoneen lisätieto" :nimi :tyokonelisatieto})]

                        (for [{:keys [toimenpide maara yksikko]} (:tehtavat toteuma)]
                          {:otsikko toimenpide
                           :hae (hakufunktio
                                  ;; Näitä ei edes tehdä jos arvot puuttuvat, joten ei
                                  ;; tarvita erityistä validointia.
                                  (constantly true)
                                  (constantly (when maara (str (fmt/desimaaliluku maara) " " yksikko))))})

                        (for [{{:keys [nimi yksikko]} :materiaali maara :maara} (:materiaalit toteuma)]
                          {:otsikko nimi
                           :hae (hakufunktio
                                  (constantly true)
                                  (constantly (when maara (str (fmt/desimaaliluku maara) " " yksikko))))})
                        (when (:lisatieto toteuma)
                          [{:otsikko "Lisätieto" :nimi :lisatieto}])))
   :data toteuma})

(defmethod infopaneeli-skeema :tietyoilmoitus [tietyoilmoitus]
  {:tyyppi :tietyoilmoitus
   :jarjesta-fn ::t-domain/alku
   :otsikko "Tietyöilmoitus"
   :tiedot [{:otsikko "Ilmoittaja" :hae (hakufunktio
                                          #{[::t-domain/ilmoittaja ::t-domain/etunimi]
                                            [::t-domain/ilmoittaja ::t-domain/sukunimi]}
                                          t-domain/ilmoittaja->str)}
            {:otsikko "Urakka" :nimi ::t-domain/urakan-nimi}
            {:otsikko "Urakoitsija" :nimi ::t-domain/urakoitsijan-nimi}
            {:otsikko "Urakoitsijan yhteyshenkilo" :hae (hakufunktio
                                                          #{[::t-domain/urakoitsijayhteyshenkilo ::t-domain/etunimi]
                                                            [::t-domain/urakoitsijayhteyshenkilo ::t-domain/sukunimi]}
                                                          t-domain/urakoitsijayhteyshenkilo->str)}
            {:otsikko "Tilaaja" :nimi ::t-domain/tilaajan-nimi}
            {:otsikko "Tilaajan yhteyshenkilö" :hae (hakufunktio
                                                      #{[::t-domain/tilaajayhteyshenkilo ::t-domain/etunimi]
                                                        [::t-domain/tilaajayhteyshenkilo ::t-domain/sukunimi]}
                                                      t-domain/tilaajayhteyshenkilo->str)}
            {:otsikko "Kunnat" :nimi ::t-domain/kunnat}
            {:otsikko "Osoite" :hae (hakufunktio
                                      #{[::t-domain/osoite ::tr-domain/aet] [::t-domain/osoite ::tr-domain/tie] [::t-domain/osoite ::tr-domain/let]}
                                      #(tr-domain/tierekisteriosoite-tekstina (::t-domain/osoite %) {:teksti-tie? false}))}
            {:otsikko "Tien nimi" :nimi ::t-domain/tien-nimi}
            {:otsikko "Alkusijainnin kuvaus" :nimi ::t-domain/alkusijainnin-kuvaus}
            {:otsikko "Loppusijainnin kuvaus" :nimi ::t-domain/loppusijainnin-kuvaus}
            {:otsikko "Alku" :nimi ::t-domain/alku :tyyppi :pvm-aika}
            {:otsikko "Loppu" :nimi ::t-domain/loppu :tyyppi :pvm-aika}
            {:otsikko "Työtyypit" :hae (hakufunktio
                                         ::t-domain/tyotyypit
                                         t-domain/tyotyypit->str)}
            #_{:otsikko "Työajat" :hae (hakufunktio
                                         ::t-domain/tyoajat
                                         t-domain/tyoajat->str)}
            #_{:otsikko "Vaikutussuunta" :nimi ::t-domain/vaikutussuunta}
            {:otsikko "Kaistajärjestelyt" :hae (hakufunktio
                                                 ::t-domain/kaistajarjestelyt
                                                 t-domain/kaistajarjestelyt->str)}
            {:otsikko "Nopeusrajoitukset" :hae (hakufunktio
                                                 ::t-domain/nopeusrajoitukset
                                                 t-domain/nopeusrajoitukset->str)}
            {:otsikko "Tienpinnat" :hae (hakufunktio
                                          ::t-domain/tienpinnat
                                          t-domain/tienpinnat->str)}
            {:otsikko "Kiertotien pituus" :nimi ::t-domain/kiertotien-pituus}
            #_{:otsikko "Kiertotien mutkaisuus" :nimi ::t-domain/kiertotien-mutkaisuus}
            {:otsikko "Kiertotien pinnat" :hae (hakufunktio
                                                 ::t-domain/kiertotienpinnat
                                                 t-domain/kiertotienpinnat->str)}
            #_{:otsikko "Liikenteenohjaus" :nimi ::t-domain/liikenteenohjaus}
            #_{:otsikko "Liikenteenohjaaja" :nimi ::t-domain/liikenteenohjaaja}
            #_{:otsikko "Viivästys normaalisti" :nimi ::t-domain/viivastys-normaali-liikenteessa}
            #_{:otsikko "Viivästys ruuhkassa" :nimi ::t-domain/viivastys-ruuhka-aikana}
            #_{:otsikko "Ajoneuvorajoitukset" :hae (hakufunktio
                                                     ::t-domain/ajoneuvorajoitukset
                                                     t-domain/ajoneuvorajoitukset->str)}
            #_{:otsikko "Huomautukset" :hae (hakufunktio
                                              ::t-domain/huomautukset
                                              t-domain/huomautukset->str)}
            #_{:otsikko "Ajoittaiset pysäytykset" :hae (hakufunktio
                                                         ::t-domain/ajoittaiset-pysaytykset
                                                         t-domain/ajoittaiset-pysaytykset->str)}
            #_{:otsikko "Ajoittain suljettu tie" :hae (hakufunktio
                                                        ::t-domain/ajoittain-suljettu-tie
                                                        t-domain/ajoittain-suljettu-tie->str)}
            #_{:otsikko "Pysäytysten alku" :nimi ::t-domain/pysaytysten-alku}
            #_{:otsikko "Pysäytysten loppu" :nimi ::t-domain/pysaytysten-loppu}
            {:otsikko "Lisätietoja" :nimi ::t-domain/lisatietoja}
            #_{:otsikko "Urakoitsijan nimi" :nimi ::t-domain/urakoitsijan-nimi}
            ]
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

(defmethod infopaneeli-skeema :turvalaite [turvalaite]
  (let [nayta-max-toimenpidetta 10]
    {:tyyppi :turvalaite
     :jarjesta-fn (constantly false)
     :otsikko (or (::tu/nimi turvalaite) "Turvalaite")
     :tiedot (vec
               (concat
                 [{:otsikko "Turvalaitenumero" :nimi ::tu/turvalaitenro :tyyppi :string}
                  {:otsikko "Tyyppi" :nimi ::tu/tyyppi :tyyppi :string}
                  {:otsikko "Väylä" :tyyppi :string :hae
                   (hakufunktio
                     #{[:toimenpiteet 0 ::to/vayla ::v/nimi]}
                     #(get-in % [:toimenpiteet 0 ::to/vayla ::v/nimi]))}
                  {:otsikko "Tehdyt toimenpiteet" :nimi :toimenpiteet :tyyppi :komponentti
                   :komponentti
                   (fn []
                     (let [kaikkia-ei-piirretty? (> (count (:toimenpiteet turvalaite)) nayta-max-toimenpidetta)]
                       [:div
                        (for [toimenpide (sort-by ::to/suoritettu pvm/jalkeen? (take nayta-max-toimenpidetta (:toimenpiteet turvalaite)))]
                          ^{:key (str (::tu/turvalaitenro turvalaite) "-" (::to/id toimenpide))}
                          [:div (str (if-let [s (or (::to/pvm toimenpide)
                                                    (::to/suoritettu toimenpide))]
                                       (pvm/pvm s)
                                       "-")
                                     " - " (to/reimari-tyolaji-fmt (::to/tyoluokka toimenpide))
                                     " - " (to/reimari-toimenpidetyyppi-fmt (::to/toimenpide toimenpide)))])
                        (when kaikkia-ei-piirretty?
                          [:div (str "...sekä "
                                     (- (count (:toimenpiteet turvalaite)) nayta-max-toimenpidetta)
                                     " muuta toimenpidettä.")])
                        ]))}]))
     :data turvalaite}))

(defmethod infopaneeli-skeema :kohteenosa [osa]
  {:tyyppi :kohteenosa
   :jarjesta-fn (constantly false)
   :otsikko (kohde/fmt-kohde-ja-osa-nimi (::osa/kohde osa) osa)
   :tiedot [{:otsikko "Kohde"
             :tyyppi :string
             :nimi (kohde/fmt-kohteen-nimi (::osa/kohde osa))}
            (when (::osa/nimi osa)
              {:otsikko "Nimi"
               :tyyppi :string
               :nimi ::osa/nimi})
            {:otsikko "Tyyppi"
             :tyyppi :string
             :nimi ::osa/tyyppi
             :fmt (comp string/capitalize osa/fmt-kohteenosa-tyyppi)}
            {:otsikko "Oletuspalvelumuoto"
             :nimi ::osa/oletuspalvelumuoto
             :fmt liikenne/palvelumuoto->str
             :tyyppi :string}]
   :data osa})

(defmethod infopaneeli-skeema :kohde-toimenpide [kohde]
  (let [toimenpiteen-kentta (fn [otsikko kentta]
                              (when (-> kohde :toimenpiteet kentta)
                                {:otsikko otsikko
                                 :tyyppi :string
                                 :hae (hakufunktio :toimenpiteet #(-> % :toimenpiteet kentta))}))]
    {:tyyppi :kohde
     :jarjesta-fn (constantly false)
     :otsikko (kohde/fmt-kohteen-nimi kohde)
     :tiedot [(toimenpiteen-kentta "Kohteenosan tyyppi" :kohteenosan-tyyppi)
              (toimenpiteen-kentta "Huoltokohde" :huoltokohde)
              (if (-> kohde :toimenpiteet :muu-toimenpide)
                (toimenpiteen-kentta "Toimenpide" :muu-toimenpide)
                (toimenpiteen-kentta "Toimenpide" :toimenpide))
              (toimenpiteen-kentta "Lisatieto" :lisatieto)
              (toimenpiteen-kentta "Suorittaja" :suorittaja)
              (toimenpiteen-kentta "Kuittaaja" :kuittaaja)
              (toimenpiteen-kentta "Päivämäärä" :pvm)]
     :data kohde}))

(defmethod infopaneeli-skeema :kan-hairiotilanne [hairiotilanne]
  {:tyyppi :kan-hairiotilanne
   :jarjesta-fn ::ht/havaintoaika
   :otsikko (str
              (pvm/pvm (::ht/havaintoaika hairiotilanne))
              " "
              (ht/fmt-korjauksen-tila (get-in hairiotilanne [::ht/korjauksen-tila]))
              " häiriötilanne")
   :tiedot [{:otsikko "Vikaluokka" :nimi ::ht/vikaluokka :tyyppi :string :fmt ht/fmt-vikaluokka}
            {:otsikko "Syy" :nimi ::ht/syy :tyyppi :string}
            {:otsikko "Korjaustoimenpide" :nimi ::ht/korjaustoimenpide :tyyppi :string}
            {:otsikko "Korjausaika" :nimi ::ht/korjausaika-h :tyyppi :string}
            {:otsikko "Odotusaika" :nimi ::ht/odotusaika-h :tyyppi :string}
            {:otsikko "Ammattiliikenne lkm" :nimi ::ht/ammattiliikenne-lkm :tyyppi :string}
            {:otsikko "Huviliikenne lkm" :nimi ::ht/huviliikenne-lkm :tyyppi :string}
            {:otsikko "Kuittaaja" :hae (hakufunktio
                                         #{[::ht/kuittaaja ::kayttaja/etunimi]
                                           [::ht/kuittaaja ::kayttaja/sukunimi]}
                                         (fn [ht]
                                           (str
                                             (get-in ht [::ht/kuittaaja ::kayttaja/etunimi])
                                             " "
                                             (get-in ht [::ht/kuittaaja ::kayttaja/sukunimi]))))}]
   :data hairiotilanne})

(defmethod infopaneeli-skeema :kan-toimenpide [toimenpide]
  {:tyyppi :kan-toimenpide
   :jarjesta-fn ::kan-to/pvm
   :otsikko (str
              (pvm/pvm (::kan-to/pvm toimenpide))
              " "
              (or
                (get-in toimenpide [::kan-to/muu-toimenpide])
                (get-in toimenpide [::kan-to/toimenpidekoodi ::tpk/nimi])))
   :tiedot [{:otsikko "Tehtävä"
             :tyyppi :string
             :hae (hakufunktio #{[::kan-to/toimenpidekoodi ::tpk/nimi]} #(get-in % [::kan-to/toimenpidekoodi ::tpk/nimi]))}
            {:otsikko "Pvm" :nimi ::kan-to/pvm :tyyppi :pvm}
            {:otsikko "Huoltokohde"
             :hae (hakufunktio #{[::kan-to/huoltokohde ::huoltokohde/nimi]}
                               #(get-in % [::kan-to/huoltokohde ::huoltokohde/nimi]))
             :tyyppi :string}
            {:otsikko "Suorittaja" :nimi ::kan-to/suorittaja :tyyppi :string}
            {:otsikko "Lisätieto" :nimi ::kan-to/lisatieto :tyyppi :string}]
   :data toimenpide})

(defmethod infopaneeli-skeema :kohde-hairiotilanne [kohde]
  (let [hairiotilanteen-kentta (fn [otsikko kentta]
                                 (when (-> kohde :hairiot kentta)
                                   {:otsikko otsikko
                                    :tyyppi :string
                                    :hae (hakufunktio :hairiot #(-> % :hairiot kentta))}))]
    {:tyyppi :kohde
     :jarjesta-fn (constantly false)
     :otsikko (kohde/fmt-kohteen-nimi kohde)
     :tiedot [(hairiotilanteen-kentta "Kohde" :kohde)
              (hairiotilanteen-kentta "Havaintoaika" :havaintoaika)
              (hairiotilanteen-kentta "Korjauksen tila" :korjauksen-tila)
              (hairiotilanteen-kentta "Vikaluokka" :vikaluokka)
              (hairiotilanteen-kentta "Korjaustoimenpide" :korjaustoimenpide)
              (hairiotilanteen-kentta "Korjausaika (h)" :korjausaika-h)
              (hairiotilanteen-kentta "Syy" :syy)
              (hairiotilanteen-kentta "Kuittaaja" :kuittaaja)]
     :data kohde}))

(defmethod infopaneeli-skeema :suolatoteuma [suolatoteuma]
  {:tyyppi :suolatoteuma
   :jarjesta-fn (let [fn :alkanut]
                  (if (fn suolatoteuma)
                    fn
                    (constantly false)))
   :otsikko (str (when (:alkanut suolatoteuma)
                                  (str " " (pvm/pvm (:alkanut suolatoteuma))))
                 "Suolatoteuma")
   :tiedot [{:otsikko "Materiaali" :nimi :materiaali_nimi}
            {:otsikko "Määrä (t)" :nimi :maara}
            {:otsikko "Alkanut" :nimi :alkanut :tyyppi :pvm-aika}
            {:otsikko "Päättynyt" :nimi :paattynyt :tyyppi :pvm-aika}]
   :data suolatoteuma})

(defmethod infopaneeli-skeema :tielupa [lupa]
  {:tyyppi :tielupa
   :jarjesta-fn ::tielupa/myontamispvm
   :tunniste ::tielupa/id
   :otsikko (str (when-let [m (::tielupa/myontamispvm lupa)] (pvm/pvm-opt m)) " " (::tielupa/paatoksen-diaarinumero lupa))
   :tiedot [{:otsikko "Tyyppi" :nimi ::tielupa/tyyppi :fmt tielupa/tyyppi-fmt}
            {:otsikko "Voimassaolon alku" :nimi ::tielupa/voimassaolon-alkupvm :tyyppi :pvm-aika}
            {:otsikko "Voimassaolon loppu" :nimi ::tielupa/voimassaolon-loppupvm :tyyppi :pvm-aika}
            {:otsikko "Hakija" :tyyppi :string :nimi ::tielupa/hakija-tyyppi}
            {:otsikko "Nimi" :tyyppi :string :nimi ::tielupa/hakija-nimi}
            {:otsikko "Puhelinnumero" :tyyppi :string :nimi ::tielupa/hakija-puhelinnumero}
            {:otsikko "Sähköpostiosoite" :tyyppi :string :nimi ::tielupa/hakija-sahkopostiosoite}
            {:otsikko "TR-osoitteet"
             :nimi ::tielupa/sijainnit
             :tyyppi :komponentti
             :komponentti
             (fn []
               (let [sijainnit (::tielupa/sijainnit lupa)]
                 (into
                   [:span
                    {:style {:padding-right "55px"
                             :float "right"}}]
                   (map-indexed
                     (fn [i osoite]
                       ^{:key (str i "_" osoite)}
                       [:div osoite])
                     (->> sijainnit
                          (sort-by (juxt ::tielupa/tie
                                         ::tielupa/aosa
                                         ::tielupa/aet
                                         ::tielupa/losa
                                         ::tielupa/let))
                          (map (juxt ::tielupa/tie
                                     ::tielupa/aosa
                                     ::tielupa/aet
                                     ::tielupa/losa
                                     ::tielupa/let))
                          (map (partial keep identity))
                          (map (partial string/join "/")))))))}]
   :data lupa})

(defmethod infopaneeli-skeema :default [x]
  (log/warn "infopaneeli-skeema metodia ei implementoitu tyypille " (pr-str (:tyyppi-kartalla x))
            ", palautetaan tyhjä itemille " (pr-str x))
  nil)

(defmethod infopaneeli-skeema :paikkaukset-toteumat [paikkaus]
  (let [toteuman-kentta (fn [skeema avain]
                          (when (-> paikkaus :infopaneelin-tiedot avain)
                            skeema))]
    {:data (:infopaneelin-tiedot paikkaus)
     :otsikko "Paikkaus"
     :jarjesta-fn ::paikkaus/alkuaika
     :tyyppi :paikkaukset-toteumat
     :tiedot [{:otsikko "Päällystyskohde" :nimi ::paikkaus/nimi}
              {:otsikko "Tierekistetriosoite"
               :tyyppi :string
               :hae (hakufunktio #{::tr-domain/tie ::tr-domain/aosa ::tr-domain/aet ::tr-domain/losa ::tr-domain/let}
                                 #(apply str (interpose ", "
                                                        [(::tr-domain/tie %) (::tr-domain/aosa %) (::tr-domain/aet %)
                                                         (::tr-domain/losa %) (::tr-domain/let %)])))}
              (toteuman-kentta {:otsikko "Leveys" :nimi ::paikkaus/leveys}
                               ::paikkaus/leveys)
              (toteuman-kentta {:otsikko "Ajoura" :nimi ::paikkaus/ajourat}
                               ::paikkaus/ajourat)
              (toteuman-kentta {:otsikko "Ajorata" :nimi ::paikkaus/ajorata}
                               ::paikkaus/ajorata)
              (toteuman-kentta {:otsikko "Reuna"
                                :tyyppi :string
                                :hae (hakufunktio ::paikkaus/reunat #(apply str (interpose ", " (::paikkaus/reunat %))))}
                               ::paikkaus/reunat)
              (toteuman-kentta {:otsikko "Ajouravälit"
                                :tyyppi :string
                                :hae (hakufunktio ::paikkaus/ajouravalit #(apply str (interpose ", " (::paikkaus/ajouravalit %))))}
                               ::paikkaus/ajouravalit)
              (toteuman-kentta {:otsikko "Alkuaika" :nimi ::paikkaus/alkuaika :tyyppi :pvm-aika}
                               ::paikkaus/alkuaika)
              (toteuman-kentta {:otsikko "Loppuaika" :nimi ::paikkaus/loppuaika :tyyppi :pvm-aika}
                               ::paikkaus/loppuaika)
              (toteuman-kentta {:otsikko "Raekoko" :nimi ::paikkaus/raekoko}
                               ::paikkaus/raekoko)
              (toteuman-kentta {:otsikko "Massatyyppi" :nimi ::paikkaus/massatyyppi}
                               ::paikkaus/massatyyppi)
              (toteuman-kentta {:otsikko "Kuulamylly" :nimi ::paikkaus/kuulamylly}
                               ::paikkaus/kuulamylly)
              (toteuman-kentta {:otsikko "Massamenekki" :nimi ::paikkaus/massamenekki}
                               ::paikkaus/massamenekki)]}))

(defn- rivin-skeemavirhe [viesti rivin-skeema infopaneeli-skeema]
  (do
    (log/debug viesti
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
       (do (log/warn (str "Otsikko puuttuu " (pr-str infopaneeli-skeema)))
           nil)

       (nil? jarjesta-fn)
       (do (log/warn (str "jarjesta-fn puuttuu tiedolta " (pr-str infopaneeli-skeema)))
           nil)

       (empty? validit-skeemat)
       (do (log/warn (str "Tiedolla ei ole yhtään validia skeemaa: " (pr-str infopaneeli-skeema)))
           nil)

       (and vaadi-kaikki-skeemat? (pos? epaonnistuneet-skeemat-lkm))
       (do
         (log/warn
           (str
             epaonnistuneet-skeemat-lkm
             " puutteellista skeemaa, kun yksikään ei saisi epäonnistua "
             (pr-str data)))
         nil)

       ;; Järjestäminen yritetään tehdä avaimella, jota ei datasta löydy
       (nil? (jarjesta-fn data))
       (do
         (log/warn (str "jarjesta-fn on määritelty, mutta avaimella ei löydy dataa skeemasta " (pr-str infopaneeli-skeema)))
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

(defn vain-uniikit
  "Jos skeemalle on annettu :tunniste, ja samalla tunnisteella löytyy
  monta riviä, palauttaa vain yhden riveistä."
  [skeemat]
  (let [[tunnisteelliset tunnisteettomat] ((juxt #(get % true) #(get % false))
                                            (group-by (comp some? :tunniste) skeemat))]
    (concat
      (map
        first
        (mapcat
          vals
          (map
            (fn [[tunniste rivit]]
              (group-by (comp tunniste :data) rivit))
            (group-by :tunniste tunnisteelliset))))
      tunnisteettomat)))

(defn skeemamuodossa
  ([asiat]
   (as-> asiat $
         (keep infopaneeli-skeema $)
         (map skeema-ilman-tyhjia-riveja $)
         (keep validoi-infopaneeli-skeema $)
         (vain-uniikit $)
         (sort-by #((:jarjesta-fn %) (:data %)) jarjesta $)
         (vec $))))
