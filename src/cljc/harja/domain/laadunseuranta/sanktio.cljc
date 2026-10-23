;; DEPRECATED: Tämä tiedosto on deprecated. Älä enää käytä uusissa ominaisuuksissa.
;; Käytä sanktio_profiili- ja bonus_profiili-tauluja suoraan.

(ns harja.domain.laadunseuranta.sanktio
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]))

;; -> Ehtolauseilla hallitaan mitä subsettejä lajeista näytetään missäkin näkymässä mhu XXXX- #{:A :B :C ...}
;; Ehtolauseilla voidaan myös hallita miten mikäkin sanktiotyyppi asettuu tietyn lajin alle eri urakkatyyppeillä/vuosikerroilla
;; mikäli on tarve.
;; NOTE: Sanktiotyyppi-taulun tila 2022-09-21 (koodi, nimi):
;; 0,Ei tarvita sanktiotyyppiä
;; 1,Muu tuote
;; 2,Talvihoito
;; 3,Ylläpidon sakko
;; 4,Ylläpidon bonus
;; 5,Ylläpidon muistutus
;; 6,Vesiväylän sakko
;; 7,Suolasakko
;; 8,Määräpäivän ylitys
;; 9,Työn tekemättä jättäminen
;; 10,Hallinnolliset laiminlyönnit
;; 11,Muu sopimuksen vastainen toiminta
;; 12,Asiakirjamerkintöjen paikkansa pitämättömyys
;; 13,"Talvihoito, päätiet"
;; 14,"Talvihoito, muut tiet"
;; 15,Liikenneympäristön hoito
;; 16,Sorateiden hoito ja ylläpito
;; 17,Muut hoitourakan tehtäväkokonaisuudet
(defn sanktiolaji->sanktiotyyppi-koodi
  [sanktiolaji-kw urakan-alkupvm]

  (let [laji->koodi {:muistutus
                     (vec (concat
                            [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                             #_1
                             ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                             #_2
                             13 14]
                            ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2021)
                            ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2020 tai pienempi.
                            (when (< (pvm/vuosi urakan-alkupvm) 2021)
                              [15 16])

                            ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2021)
                            ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2021 tai suurempi
                            (when (>= (pvm/vuosi urakan-alkupvm) 2021)
                              [17])
                            ;; Muistutuksille lisätään vielä Hallinnolliset laiminlyönnit
                            [10]))
                     :A (vec (concat
                               [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_1
                                ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_2

                                13 14]
                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2021)
                               ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2020 tai pienempi.
                               (when (< (pvm/vuosi urakan-alkupvm) 2021)
                                 [15 16])

                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2021)
                               ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2020 tai suurempi
                               (when (>= (pvm/vuosi urakan-alkupvm) 2021)
                                 [17])))
                     :B (vec (concat
                               [;; Muu tuote, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_1
                                ;; Talvihoito, poistettu kaikilta urakoilta. Tietokannassa merkitty poistetuksi.
                                #_2

                                13 14]
                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Poistetaan urakoista 2020)
                               ;; 15, "Liikenneympäristön hoito" ja 16, "Sorateiden hoito ja ylläpito" mukana urakoissa, joiden alkuvuosi on 2019 tai pienempi.
                               (when (< (pvm/vuosi urakan-alkupvm) 2021)
                                 [15 16])

                               ;; Figma: Sanktiolajien, -tyyppien ja toimenpiteiden valinnat (Uusi, lisätään urakoille 2020)
                               ;; 17, "Muut hoitourakan tehtäväkokonaisuudet" mukana urakoissa, joiden alkuvuosi on 2020 tai suurempi
                               (when (>= (pvm/vuosi urakan-alkupvm) 2021)
                                 [17])))

                     :C [8 9 10 11 12]

                     :pohjavesisuolan_ylitys [7]
                     :talvisuolan_ylitys [7]

                     :yllapidon_sakko [3]
                     :yllapidon_bonus [4]
                     :yllapidon_muistutus [5]

                     :vesivayla_sakko [6]
                     ;; Näitä ei ollut sanktiotyyppi-tietokantataulussa tuotannossa käytössä, joten disabloituna tässä
                     #_#_:vesivayla_bonus []
                     #_#_:vesivayla_muistutus []

                     :lupaussanktio [0]
                     :tenttikeskiarvo-sanktio [0]
                     :testikeskiarvo-sanktio [0]
                     :vaihtosanktio [0]
                     :arvonvahennyssanktio [0]}]
    (laji->koodi sanktiolaji-kw)))

(defn sanktiolaji->sanktiotyypit
  "Suodattaa kaikki-sanktiotyypit sekvenssistä lajin ja lajiin kuuluvien sanktiotyyppien koodien avulla
  koodia vastaavat mapit. 'Kaikki-sanktiotyypit' sisältää sanktiotyyppien kaikki tiedot mappeina.

  Laji -> tyyppi hierarkia on siis määritelty esim. näin: {:A [1 8 12]}, jossa numerot ovat sanktiotyyppien uniikkeja koodeja.
  Tietokannassa on sanktiotyyppien koodeilla löytyvät tiedot esim. {:nimi ... :id ... :toimenpidekoodi ... :koodi ...}

  Käyttöliittymän valikkoja varten tämä funktio huolehtii myös siitä, että funktio paluuarvona
  palautetaan sanktiotyypit vastaavassa järjestyksessä, kuin \"sanktiolaji->sanktiotyyppi-koodi\" mapissa on määritelty.

  Parametrit:
    laji (keyword) Sanktiolaji
    kaikki-sanktiotyypit (seq) Sekvenssi sanktiotyyppien dataa sisältäviä mappeja. Esim. [{:nimi ... :id ... :toimenpidekoodi ... :koodi ...}]"
  [laji-kw kaikki-sanktiotyypit urakan-alkupvm]
  (let [lajin-sanktiotyyppien-koodit (sanktiolaji->sanktiotyyppi-koodi laji-kw urakan-alkupvm)]
    (->>
      (filter
        #(some #{(:koodi %)}
           lajin-sanktiotyyppien-koodit)
        kaikki-sanktiotyypit)
      ;; Varmistetaan, että lopputulos on samassa järjestyksessä kuin alkuperäinen lajin-sanktiotyyppien-koodit
      (sort-by #(.indexOf (vec lajin-sanktiotyyppien-koodit) (:koodi %))))))

(defn mhu25-urakka?
  "Tosi, jos kyseessä on MHU25-urakka tai uudempi, eli teiden-hoito-tyyppinen urakka, jonka alkuvuosi on 2025 tai suurempi."
  [{:keys [tyyppi alkupvm]}]
  (and (= :teiden-hoito tyyppi)
    (some? alkupvm)
    (>= (pvm/vuosi alkupvm) 2025)))

(defn urakan-sanktiolajit
  "Palauttaa urakalle kuuluvat sanktiolajit urakka-tyypin mukaisesti.

   :arvonvahennyssanktio lisätään teiden-hoito-tyyppisen urakan lajeihin heti :C-sanktion jälkeen, mikäli:
   - MHU24-urakka -> aina siihen asti, että kuluvan hoitokauden alkuvuosi on 2026 (eli väliaikaisesti).

   Parametrit:
     urakka                              Urakan tiedot (mm. :tyyppi ja :alkupvm)
     kuluvan-hoitokauden-alkuvuosi       Kuluvan hoitokauden alkuvuosi (int)"
  [{:keys [tyyppi] :as urakka} kuluvan-hoitokauden-alkuvuosi]
  (let [;; Arvonvähennyssanktio näytetään tässä "vanhassa" listassa jos on mhu25+ urakka tai jos hoitovuosi on 2026 alkaen.
        nayta-arvonvahennyssanktio? (cond
                                      (mhu25-urakka? urakka) false ;; MHU25 (tai uudempi) - ei näytetä
                                      (>= kuluvan-hoitokauden-alkuvuosi 2026) false ;; Alkanut hoitovuosi 2026 - ei näytetä
                                      :else true)] ;; Kaikissa muissa tapauksissa näytetään
    (cond
      ;; MH- tai Alueurakka?
      (or (= :teiden-hoito tyyppi) (= :hoito tyyppi))
      (vec (concat
             [:muistutus :A :B :C]
             (when nayta-arvonvahennyssanktio?
               [:arvonvahennyssanktio])
             [:pohjavesisuolan_ylitys :talvisuolan_ylitys
              :tenttikeskiarvo-sanktio :testikeskiarvo-sanktio :vaihtosanktio]))

      ;; Yllapidon urakka?
      (urakka-domain/yllapitourakka? tyyppi)
      [:yllapidon_sakko :yllapidon_muistutus]

      :else [])))

(defn laatupoikkeaman-sanktiolajit
  [{:keys [tyyppi alkupvm] :as urakka}]
  (cond
    ;; Yllapidon urakka?
    (urakka-domain/yllapitourakka? tyyppi)
    [:yllapidon_sakko :yllapidon_muistutus]

    ;; MHU ja muut
    :else [:muistutus :A :B :C :arvonvahennyssanktio]))

(defn sanktio-konfiguraation-lajin-tiedot
  [sanktio-konfiguraatio laji]
  (some #(when (= laji (:laji %)) %) (:sanktio-lajit sanktio-konfiguraatio)))

(defn sanktio-konfiguraation-lajit
  [sanktio-konfiguraatio]
  (mapv :laji (:sanktio-lajit sanktio-konfiguraatio)))

(defn sanktio-konfiguraation-lajin-nimi
  [sanktio-konfiguraatio laji]
  (:nimi (sanktio-konfiguraation-lajin-tiedot sanktio-konfiguraatio laji)))

(defn sanktio-konfiguraation-sanktiotyypit
  [sanktio-konfiguraatio laji]
  (vec (:sanktiotyypit (sanktio-konfiguraation-lajin-tiedot sanktio-konfiguraatio laji))))

(defn- kiintea-automaattinen-summamaaritys?
  [{:keys [maaritystapa ohjeteksti]}]
  (and (= :automaattinen maaritystapa)
    (str/blank? ohjeteksti)))

(defn sanktiotyypilla-kiintea-automaattinen-summamaaritys?
  [{:keys [summamaaritykset]}]
  (boolean (some kiintea-automaattinen-summamaaritys? summamaaritykset)))

(defn sanktiotyypin-kiintea-automaattinen-summa
  [{:keys [summamaaritykset]}]
  (some (fn [{:keys [maaritystapa summa-euroina] :as summamaaritys}]
          (when (and (kiintea-automaattinen-summamaaritys? summamaaritys)
                  (number? summa-euroina)
                  (pos? summa-euroina)
                  #?(:clj (Double/isFinite (double summa-euroina))
                     :cljs (js/isFinite summa-euroina)))
            summa-euroina))
    summamaaritykset))

(defn kiintea-sanktiosumma
  [normaali-summa itse-ilmoitettu?]
  (if itse-ilmoitettu?
    (/ normaali-summa 2)
    normaali-summa))

;; Ainoa paikka, josta kiinteän sanktion normaalimäärä ja summa johdetaan lomakkeelle.
;; Omailmoitus puolittaa summan vain, jos profiili sallii sen.
(defn kiintea-summamalli
  [{:keys [voi-puolittaa-omailmoituksella] :as sanktiotyyppi} omailmoitettu?]
  (when-let [normaalimaara (sanktiotyypin-kiintea-automaattinen-summa sanktiotyyppi)]
    (let [omailmoitettu (boolean (and voi-puolittaa-omailmoituksella omailmoitettu?))]
      {:normaalimaara normaalimaara
       :omailmoitettu omailmoitettu
       :summa (kiintea-sanktiosumma normaalimaara omailmoitettu)})))

;; Tallennettava sanktio: kiinteä summa lisätään sellaisenaan, laskettavalla lähetetään vain raakasyöte.
(defn sanktion-tallennusmuoto
  [muokattu {:keys [kiintea-summamalli laskettu-maaritys]} lomakkeen-tiedot]
  (cond-> lomakkeen-tiedot
    kiintea-summamalli
    (merge kiintea-summamalli)

    ;; Lähetetään vain raakasyöte; palvelin laskee summan itse.
    laskettu-maaritys
    (-> (dissoc :summa :normaalimaara :laskennan-syote)
      (assoc :omailmoitettu false
        :laskettava-syote (sanktion-laskenta/laskettava-syote muokattu)))))

;; Lomakkeen summanäkymä: kiinteä johdettu summa tai manuaalinen C-ryhmän summa, ei atomeja.
(defn sanktion-summakonteksti
  [{:keys [laji omailmoitettu aktiivinen-tyyppi]}]
  (let [kiintea (kiintea-summamalli aktiivinen-tyyppi omailmoitettu)
        c-ryhman-maaritys (when (= :C laji)
                            (some (fn [{:keys [maaritystapa summa-euroina ohjeteksti]}]
                                    (when (and (= :manuaalinen maaritystapa)
                                            (some? summa-euroina)
                                            (not (str/blank? ohjeteksti)))
                                      {:summa-euroina summa-euroina
                                       :ohjeteksti ohjeteksti}))
                              (:summamaaritykset aktiivinen-tyyppi)))]
    {:kiintea-profiilisumma? (sanktiotyypilla-kiintea-automaattinen-summamaaritys? aktiivinen-tyyppi)
     :omailmoitus-sallittu? (boolean (:voi-puolittaa-omailmoituksella aktiivinen-tyyppi))
     :kiintea-summamalli kiintea
     :laskettu-maaritys (sanktion-laskenta/laskettu-summamaaritys aktiivinen-tyyppi)
     :c-ryhman-maaritys c-ryhman-maaritys}))



(def kasittelytavat [:tyomaakokous :valikatselmus :puhelin :kommentit :muu])
(def kasittelytavat-mhu25 [:valikatselmus])

(defn- viimeinen-hoitokausi-nykyhetkella?
  [urakan-loppupvm pvm]
  (when (and urakan-loppupvm pvm)
    (= (dec (pvm/vuosi urakan-loppupvm))
       (pvm/hoitokauden-alkuvuosi (pvm/joda-timeksi pvm)))))

(defn talvisuolan-validointi
  "Palauttaa talvisuolan viimeisen hoitovuoden virheen tai nilin."
  [urakan-loppupvm arvo sanktio]
  (when (and (some? arvo)
             (= :talvisuolan_ylitys (:laji sanktio))
             (not (viimeinen-hoitokausi-nykyhetkella? urakan-loppupvm arvo)))
    (str "Sanktio voidaan määrätä ainostaan urakan viimeiselle hoitovuodelle "
         (pvm/vuosi urakan-loppupvm) ".")))

(defn sanktion-lomakekonteksti
  "Ratkaisee sanktiolomakkeen kenttien nykyisen näkyvyyden ja validoinnin.

  Syötteessä annetaan sanktio, urakkatyypin boolean-konteksti,
  aikakausikonteksti sekä tarvittaessa urakan loppupäivä. Paluuarvo sisältää
  vain lomakkeen domain-päätökset; kohdistus- ja tunnistepäätökset eivät kuulu
  tähän funktioon."
  [{:keys [sanktio aktiivinen-tyyppi yllapitourakka? yllapitokohdeurakka?
           vesivaylaurakka? laskutus-ilman-laskutuskelpoisuutta?
           aikakausikonteksti urakan-loppupvm]
    :or {sanktio {}}}]
  (let [laji (:laji sanktio)
        tyyppi (or aktiivinen-tyyppi (:tyyppi sanktio))
        asiakirjamerkinta? (= :asiakirjamerkintojen_paikkansa_pitamattomyys laji)
        laskutusrajan-ylitys? (= :laskutus_yli_laskutusrajan laji)
        laskutus-ilman-laskutuskelpoisuutta? (or laskutus-ilman-laskutuskelpoisuutta?
                                                 (= :laskutus_ilman_laskutuskelpoisuutta laji))
        tyon-tekematta-jattamisen-tyyppi-22? (and (= :tyon_tekematta_jattaminen laji)
                                                  (= 22 (:koodi tyyppi)))]
    {:vesivaylaurakka? vesivaylaurakka?
     :yllapitourakka? yllapitourakka?
     :nayta-tyyppi? (not (or yllapitourakka? vesivaylaurakka?
                             asiakirjamerkinta?
                             laskutus-ilman-laskutuskelpoisuutta?
                             laskutusrajan-ylitys?))
     :nayta-tapahtumapaikka? (not (or yllapitokohdeurakka? vesivaylaurakka?
                                              laskutus-ilman-laskutuskelpoisuutta?
                                              laskutusrajan-ylitys?))
     :nayta-havaittu? (not laskutus-ilman-laskutuskelpoisuutta?)
     :nayta-laskutuskuukausi-teksti? (not yllapitourakka?)
     :nayta-perustelu? (not tyon-tekematta-jattamisen-tyyppi-22?)
     :nayta-puute-tai-laiminlyonti? yllapitourakka?
     :nayta-talvisuola-ilmoitus? (= :talvisuolan_ylitys laji)
     :salli-arvonvahennys? (:salli-arvonvahennys? aikakausikonteksti)
     :talvisuolan-validointi (fn [arvo]
                               (talvisuolan-validointi urakan-loppupvm arvo sanktio))}))

(defn aikakausikonteksti
  [urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi]
  (let [mhu25? (and (= :teiden-hoito urakkatyyppi)
                 (>= urakan-alkuvuosi 2025))]
    {:nayta-indeksi? (and (= :teiden-hoito urakkatyyppi)
                       (<= urakan-alkuvuosi 2020))
     :perinta-tapa (if (<= urakan-alkuvuosi 2024)
                     :laskutuskuukausi
                     :hoitovuosi)
     :maaraystapa? mhu25?
     :kasittelytavat (if mhu25? kasittelytavat-mhu25 kasittelytavat)
     :salli-arvonvahennys? (and (not mhu25?)
                              (< hoitokauden-alkuvuosi 2026))}))

(defn aseta-kasittely-tai-maarattypvm
  [sanktio arvo perinta-tapa]
  (let [maarattypvm? (= :maarattypvm perinta-tapa)
        perintapvm (when-not (:laskutuskuukausi-komp-tiedot sanktio)
                     (if maarattypvm?
                       (pvm/hoitokauden-loppupvm
                         (pvm/vuosi
                           (second (pvm/paivamaaran-hoitokausi arvo))))
                       arvo))]
    (assoc-in
      (cond-> (assoc sanktio :maarattypvm arvo)
        (nil? (:laskutuskuukausi-komp-tiedot sanktio))
        (assoc :perintapvm perintapvm))
      [:laatupoikkeama :paatos :kasittelyaika]
      arvo)))

(defn muu-kuin-muistutus? [sanktio]
  (and (not= :muistutus (:laji sanktio))
    (not= :yllapidon_muistutus (:laji sanktio))
    (not= :vesivayla_muistutus (:laji sanktio))))

(defn vaihda-sanktiolajia
  [rivi uusi-laji sanktiotyypit toimenpideinstanssit]
  (let [vanha-tyyppi-id (get-in rivi [:tyyppi :id])
        rivi (assoc rivi :laji uusi-laji :tyyppi nil)
        vanha-tyyppi (some #(when (= vanha-tyyppi-id (:id %)) %) sanktiotyypit)
        yksi-tyyppi (first sanktiotyypit)
        rivi (cond
               (and (= 1 (count sanktiotyypit))
                 yksi-tyyppi
                 (not= 1 (count toimenpideinstanssit)))
               (assoc rivi
                 :tyyppi yksi-tyyppi
                 :toimenpideinstanssi
                 (some #(when (= (:toimenpidekoodi yksi-tyyppi)
                                  (:toimenpidekoodi %))
                        (:tpi_id %))
                   toimenpideinstanssit))

               (and (> (count sanktiotyypit) 1)
                 vanha-tyyppi)
               (assoc rivi
                 :tyyppi vanha-tyyppi
                 :toimenpideinstanssi (some #(when (= (:toimenpidekoodi vanha-tyyppi)
                                                     (:toimenpidekoodi %))
                                             (:tpi_id %))
                                         toimenpideinstanssit))

               :else (dissoc rivi :laskettava-syote :laskennan-syote))]
    (if (and (not (muu-kuin-muistutus? rivi))
          (nil? (:tyyppi rivi)))
      (assoc rivi
        :summa nil
        :omailmoitettu false
        :toimenpideinstanssi nil
        :indeksi nil)
      rivi)))

(defn vaihda-sanktiotyyppia
  [rivi uusi-tyyppi urakan-alkuvuosi toimenpideinstanssit kulun-kohdistukset]
  (let [tyyppi-vaihtui? (not= (get-in rivi [:tyyppi :id]) (:id uusi-tyyppi))
        rivi (if (<= urakan-alkuvuosi 2024)
               (let [toimenpidekoodi (:toimenpidekoodi uusi-tyyppi)
                     toimenpideinstanssi (cond
                                           ;; Toimenpideinstanssin :id on toimenpiteen id
                                           toimenpidekoodi
                                           (:tpi_id (some #(when (= toimenpidekoodi (:id %)) %)
                                                      toimenpideinstanssit))

                                           (= 1 (count kulun-kohdistukset))
                                           (:tpi_id (first kulun-kohdistukset))

                                           :else (:toimenpideinstanssi rivi))]
                 (assoc rivi
                   :tyyppi uusi-tyyppi
                   :toimenpideinstanssi toimenpideinstanssi
                   :tpi_id toimenpideinstanssi))
               (assoc rivi :tyyppi uusi-tyyppi))
        kiintea-summa (sanktiotyypin-kiintea-automaattinen-summa uusi-tyyppi)
        manuaalinen-c? (and (= :C (:laji rivi))
                (some (fn [{:keys [maaritystapa summa-euroina ohjeteksti]}]
                  (and (= :manuaalinen maaritystapa)
                 (some? summa-euroina)
                 (not (str/blank? ohjeteksti))))
                  (:summamaaritykset uusi-tyyppi)))
        omailmoitettu (if kiintea-summa
                        (:omailmoitettu rivi)
                        false)
        rivi (if (or (not tyyppi-vaihtui?)
                   (contains? rivi :omailmoitettu)
                   kiintea-summa)
               (assoc rivi :omailmoitettu omailmoitettu)
               rivi)]
    (cond-> rivi
      tyyppi-vaihtui?
      (dissoc :laskettava-syote :laskennan-syote)

      (and tyyppi-vaihtui? kiintea-summa)
      (assoc :normaalimaara kiintea-summa
             :summa (kiintea-sanktiosumma kiintea-summa omailmoitettu))

      (and tyyppi-vaihtui? manuaalinen-c?)
      (dissoc :normaalimaara :summa)

      (and tyyppi-vaihtui? (nil? kiintea-summa) (not manuaalinen-c?))
      (as-> rivi
        (cond-> rivi
          (contains? rivi :normaalimaara) (assoc :normaalimaara nil)
          (contains? rivi :summa) (assoc :summa nil))))))

(defn sakkoryhmasta-sakko? [sanktio]
  (and (not= :muistutus (:sakkoryhma sanktio))
    (not= :yllapidon_muistutus (:sakkoryhma sanktio))))

(defn paatos-on-sanktio? [sanktio]
  (= :sanktio (get-in sanktio [:paatos :paatos])))

(def +yllapidon-sanktiofraasit+
  (sort-by
    second
    ;; ao. lista perustuu ylläpidon sopimusasiakirjoihin vuodelta 2016.
    [[:valitavoitteen-viivastyminen "Välitavoitteen viivästyminen"]
     [:laatusuunnitelman-vastainen-toiminta "Laatusuunnitelman vastainen toiminta työmaalla"]
     [:toistuva-laatusuunnitelman-vastainen-toiminta "Toistuva laatusuunnitelman vastainen toiminta työmaalla"]
     [:laadunvalvontaan-liittyvien-mittausten-ym-toimien-laiminlyonnit "Laadunvalvontaan liittyvien mittausten ym. toimien laiminlyönnit"]
     [:ymparistoasioihin-liittyvat-laiminlyonnit "Ympäristöasioihin liittyvät laiminlyönnit"]
     [:tilaajan-pistokoe-alitus-jota-ei-urakoitsijan-laatupoikkeamaraportissa "Tilaajan pistokokeella havaittu laadunalitukset, jota ei urakoitsijan laatupoikkeamaraportissa"]
     [:ei-poikkeamaraporttia-heti-poikkeaman-havaitsemisen-jalkeen "Laatupoikkeamasta ei tehty poikkeamaraporttia  havaitsemisen jälkeen, laatupoikkeama jäi seuraavassa työvaiheessa piiloon"]
     [:tyoskentelyaikaan-tai-kohteiden-yhtajaksoiseen-valmistumiseen-liittyvat-puutteet-tai-laiminlyonnit "Työskentelyaikaan tai kohteiden yhtäjaksoiseen valmistumiseen liittyvät puutteet tai laiminlyönnit"]
     [:urakoitsija-ei-ole-toimittanut-tyovaihekohtaista-laatusuunnitelmaa-ennen-tyon-aloittamista "Urakoitsija ei toimittanut työvaihekohtaista laatusuunnitelmaa ennen työn aloittamista"]
     [:tuotevaatimusten-vastainen-toiminta-joka-vaikuttaa-lopputuotteen-toimivuuteen "Tuotevaatimusten vastainen toiminta, joka vaikuttaa lopputuotteen toimivuuteen"]
     [:urakoitsijan-laatujarjestelman-mukaisessa-asiakirjassa-todennettavasti-tosiasioita-vastaamattomia-tietoja "Urakoitsijan laatujärjestelmän mukaisessa asiakirjassa todennettavasti on kirjattu tosiasioita vastaamattomia tietoja"]
     [:liikenteenhoitoon-tyonaikaisista-liikennejarjestelyista-tiedottamiseen-tai-tyoturvallisuuteen-liittyvat-puutteet-tai-laiminlyonnit "Liikenteenhoitoon, työnaikaisista liikennejärjestelyistä tiedottamiseen tai työturvallisuuteen liittyvät puutteet tai laiminlyönnit"]
     [nil "Ei kuvausta"]]))

(defn sanktiofraasi-avaimella
  [fraasin-avain]
  (first (filter #(= (first %) (keyword fraasin-avain))
           +yllapidon-sanktiofraasit+)))

(defn yllapidon-sanktiofraasin-nimi
  [fraasin-avain]
  (second
    (sanktiofraasi-avaimella fraasin-avain)))

(defn sanktiolaji->teksti
  [laji]
  (case laji
    :A "A-ryhmä (tehtäväkohtainen sanktio)"
    :B "B-ryhmä (vakava laiminlyönti)"
    :C "C-ryhmä (määräpäivän ylitys, hallinnollinen laiminlyönti jne.)"
    :muistutus "Muistutus"
    :vaihtosanktio "Vastuuhenkilön vaihto"
    :testikeskiarvo-sanktio "Vastuuhenkilön testipistemäärän alentuminen"
    :tenttikeskiarvo-sanktio "Vastuuhenkilön tenttipistemäärän alentuminen"
    :arvonvahennyssanktio "Arvonvähennys"
    :pohjavesisuolan_ylitys "Pohjavesialueen suolankäytön ylitys"
    :talvisuolan_ylitys "Talvisuolan kokonaiskäytön ylitys"

    ;; MHU2026-uudet sanktiolajit - varmistetaan raporttien toimivuus
    :tyon_tekematta_jattaminen "Työn tekemättä jättäminen"
    :asiakirjamerkintojen_paikkansa_pitamattomyys "Asiakirjamerkintöjen paikkansa pitämättömyys"
    :muu_sopimuksen_vastainen_toiminta "Muu sopimuksen vastainen toiminta"
    :talvisuolan_kokonaiskayton_ylitys "Talvisuolan kokonaiskäytön ylitys"
    :laskutus_yli_laskutusrajan "Laskutus yli laskutusrajan"
    :laskutus_ilman_laskutuskelpoisuutta "Laskutus ilman laskutuskelpoisuutta"
    :vastuuhenkilon_tenttipistemaara_alentuminen "Vastuuhenkilön tenttipistemäärän alentuminen"
    :vastuuhenkilon_vaihto "Vastuuhenkilön vaihto"

    :lupaussanktio "Lupaussanktio"
    :yllapidon_muistutus "Muistutus"
    :yllapidon_sakko "Sakko"
    :yllapidon_bonus "Bonus"
    :vesivayla_muistutus "Muistutus"
    :vesivayla_sakko "Sakko"
    :vesivayla_bonus "Bonus"

    :lupausbonus "Lupausbonus"
    :alihankintabonus "Bonus alihankintasopimusten maksuehdoista"
    :asiakastyytyvaisyysbonus "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta"
    :liikennevahinkojen_aiheuttajien_selvitysbonus "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
    :muu-bonus "Muu bonus (vahingonkorvaus, liikennevahingot jne.)"
    nil))

(defn bonuslaji->teksti
  "Erilliskustannustyypin (ja 'yllapidon_bonus' sanktion) teksti avainsanaa vastaan"
  [avainsana]
  (case avainsana
    :asiakastyytyvaisyysbonus "Bonus tienkäyttäjien hyvästä palvelusta ja urakoitsijan innovatiivisuudesta"
    :liikennevahinkojen_aiheuttajien_selvitysbonus "Bonus liikennevahinkojen aiheuttajien selvittämisestä"
    :muu-bonus "Muu bonus (vahingonkorvaus, liikennevahingot jne.)"
    :alihankintabonus "Bonus alihankintasopimusten maksuehdoista"
    :tavoitepalkkio "Tavoitepalkkio"
    :lupausbonus "Lupausbonus"
    ;; Hox: Ylläpitourakoilla on aina vain yksi "bonustyyppi" vaihtoehtona, joka on poikkeuksellisesti sanktio.
    ;; Eli, tämä tyyppi ei ole yksi erilliskustannustyypeistä, vaan yksi sanktiolajeista.
    :yllapidon_bonus "Ylläpidon bonus"
    nil))

(defn kasittelytapa->teksti
  [avain]
  (case avain
    :tyomaakokous "Työmaakokous"
    :valikatselmus "Välikatselmus"
    :puhelin "Puhelimitse"
    :kommentit "Harja-kommenttien perusteella"
    :muu "Muu tapa"
    :ei-tiedossa "Ei tiedossa"
    nil))

(defn luo-kustannustyypit [urakkatyyppi kayttaja toimenpideinstanssi]
  ;; Ei sallita urakoitsijan antaa itselleen bonuksia
  ;; Eikä sallita teiden-hoito tyyppisille urakoille kaikkia bonustyyppejä valita miten halutaan vaan hallinnollisille
  ;; toimenpiteille on omat bonukset, MHU Ylläpito toimenpideinstanssille on omansa ja muille toimenpideinstansseille on vain "muu" erilliskustannus
  (filter #(if (= "urakoitsija" (get-in kayttaja [:organisaatio :tyyppi]))
             (= :muu-bonus %)
             true)
    (cond
      (urakka-domain/alueurakka? urakkatyyppi)
      [:asiakastyytyvaisyysbonus :muu-bonus]
      ;; Hoidon johto
      (and (urakka-domain/mh-urakka? urakkatyyppi) (= "23150" (:t2_koodi toimenpideinstanssi)))
      [:asiakastyytyvaisyysbonus :alihankintabonus :muu-bonus] ;; :tavoitepalkkio :lupausbonus (25.11.2020 piilossa kunnes prosessi selvänä.)
      ;; MHU Ylläpito
      (and (urakka-domain/mh-urakka? urakkatyyppi) (= "20190" (:t2_koodi toimenpideinstanssi)))
      [:asiakastyytyvaisyysbonus :alihankintabonus :muu-bonus]
      (and (urakka-domain/mh-urakka? urakkatyyppi) (not= "23150" (:t2_koodi toimenpideinstanssi)))
      [:muu-bonus]

      ;; Hox: Ylläpitourakoilla on aina vain yksi "bonustyyppi" vaihtoehtona, joka on poikkeuksellisesti sanktiolaji.
      ;;      Tätä ei tallenneta erilliskustannus-tauluun, vaan sanktio-tauluun.
      (urakka-domain/yllapitourakka? urakkatyyppi)
      [:yllapidon_bonus]

      :else
      [:asiakastyytyvaisyysbonus :muu-bonus])))

(defn- bonus? [rivi]
  (boolean (:bonus? rivi)))

(defn- sanktio? [rivi]
  (not (:bonus? rivi)))

(defn- arvonvahennys? [rivi]
  (= :arvonvahennyssanktio (:laji rivi)))

(defn muistutus? [rivi]
  (or
    (= :vesivayla_muistutus (:laji rivi))
    (= :muistutus (:laji rivi))
    (= :yllapidon_muistutus (:laji rivi))))

(defn rivin-tyyppi [rivi]
  (cond
    (muistutus? rivi) :muistutukset
    (bonus? rivi) :bonukset
    (arvonvahennys? rivi) :arvonvahennykset
    (sanktio? rivi) :sanktiot))

(defn arvonvahennykset-vaikuttaa-tavoitehintaan?
  "MHU25 urakoille - tai jos Jos kuluva vuosi 2026 -> true"
  [valittu-urakka kuluva-hoitokausi]
  (let [mhu25? (and (or (= :teiden-hoito (:tyyppi valittu-urakka)) (= "teiden-hoito" (:tyyppi valittu-urakka)))
                 (>= (pvm/vuosi (:alkupvm valittu-urakka)) 2025))
        kuluva-alkanut-hoitovuosi (pvm/vuosi (first kuluva-hoitokausi))]
    (if (or mhu25? (>= kuluva-alkanut-hoitovuosi 2026)) true false)))
