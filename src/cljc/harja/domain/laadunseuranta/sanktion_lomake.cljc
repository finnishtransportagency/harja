(ns harja.domain.laadunseuranta.sanktion-lomake
  (:require [harja.pvm :as pvm]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktion-summamaaritys :as summamaaritys]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]))

#?(:clj
   (defn- pvm-hoitokauden-alkuvuosi
     [pvm]
     (pvm/hoitokauden-alkuvuosi (pvm/joda-timeksi pvm)))
   :cljs
   (defn- pvm-hoitokauden-alkuvuosi
     [pvm]
     (pvm/hoitokauden-alkuvuosi pvm)))

(defn- viimeinen-hoitokausi-nykyhetkella?
  [urakan-loppupvm pvm]
  (when (and urakan-loppupvm pvm)
    (= (dec (pvm/vuosi urakan-loppupvm))
       (pvm-hoitokauden-alkuvuosi pvm))))

(defn talvisuolan-validointi
  "Palauttaa talvisuolan viimeisen hoitovuoden virheen tai nil."
  [urakan-loppupvm arvo sanktio]
  (when (and urakan-loppupvm
          (some? arvo)
          #?(:clj (pvm/joda-timeksi arvo)
             :cljs arvo)
          (= :talvisuolan_ylitys (:laji sanktio))
          (not (viimeinen-hoitokausi-nykyhetkella? urakan-loppupvm arvo)))
    (str "Sanktio voidaan määrätä ainostaan urakan viimeiselle hoitovuodelle "
      (pvm/vuosi urakan-loppupvm) ".")))

(defn sanktion-tallennusmuoto
  [muokattu {:keys [kiintea-summamalli laskettu-maaritys]} lomakkeen-tiedot]
  (cond-> lomakkeen-tiedot
    kiintea-summamalli
    (merge kiintea-summamalli)
    laskettu-maaritys
    (-> (dissoc :summa :normaalimaara :laskennan-syote)
      (assoc :omailmoitettu false
        :laskettava-syote (sanktion-laskenta/laskettava-syote muokattu)))))

(defn sanktion-summakonteksti
  [{:keys [laji omailmoitettu aktiivinen-tyyppi]}]
  {:kiintea-profiilisumma? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? aktiivinen-tyyppi)
   :omailmoitus-sallittu? (boolean (:voi-puolittaa-omailmoituksella aktiivinen-tyyppi))
   :kiintea-summamalli (summamaaritys/kiintea-summamalli aktiivinen-tyyppi omailmoitettu)
   :laskettu-maaritys (sanktion-laskenta/laskettu-summamaaritys aktiivinen-tyyppi)
   :c-ryhman-maaritys (summamaaritys/c-ryhman-manuaalinen-maaritys laji aktiivinen-tyyppi)})

(declare hoitovuodet)

(defn aikakausikonteksti
  "Urakan alkuvuoden, hoitokauden ja urakkatyypin mukaiset lomakeehdot.
  Kun urakan loppupäivä annetaan, mukana on myös urakan hoitovuosien alkuvuodet."
  ([urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi]
   (let [mhu25? (and (= :teiden-hoito urakkatyyppi)
                  (>= urakan-alkuvuosi 2025))]
     {:nayta-indeksi? (and (= :teiden-hoito urakkatyyppi)
                        (<= urakan-alkuvuosi 2020))
      :perinta-tapa (if (<= urakan-alkuvuosi 2024)
                      :laskutuskuukausi
                      :hoitovuosi)
      :maaraystapa? mhu25?
      :kasittelytavat (if mhu25? sanktio-domain/kasittelytavat-mhu25 sanktio-domain/kasittelytavat)
      :salli-arvonvahennys? (and (not mhu25?)
                              (< hoitokauden-alkuvuosi 2026))}))
  ([urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi urakan-loppupvm]
   (assoc (aikakausikonteksti urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi)
     :hoitovuodet (hoitovuodet urakan-alkuvuosi urakan-loppupvm))))

(defn sallitut-sanktiolajit
  "Arvonvähennys kuuluu urakan sanktioihin, mutta ei sanktiolomakkeelle silloin, kun sille on oma lomake
  (MHU25+ ja hoitovuodesta 2026 alkaen)."
  [{:keys [salli-arvonvahennys?]} sanktiolajit]
  (if salli-arvonvahennys?
    sanktiolajit
    (vec (remove #(= :arvonvahennyssanktio %) sanktiolajit))))

(defn lukutila?
  "Laatupoikkeaman kautta tehdyn sanktion lukutila välitetään sanktion tiedoissa.
  Suorasanktion lukutila tulee aina lomakkeen parametrista."
  [suorasanktio? sanktio lukutila-parametri]
  (boolean
    (if (and (not suorasanktio?) (:lukutila? sanktio))
      (:lukutila? sanktio)
      lukutila-parametri)))

(def nakymakontekstin-avaimet
  "Sanktiolomakkeen kenttäkuvausfunktioiden yhteinen konteksti. Arvo voi olla nil, avain ei saa puuttua."
  [:sanktio :lomake :aikakausi :summa :lukutila? :voi-muokata?
   :sanktiolajit :tyyppi-valinnat :urakan-alkuvuosi :toimenpideinstanssit
   :kulun-kohdistukset :hoidonjohtopalkkio-tr :laskutuskuukaudet :laskutuskuukausi-id
   :indeksi-kaytossa? :urakan-indeksi :manuaalinen-laskettavan-sisartyyppi?])

(defn nakymakonteksti
  "Palauttaa kontekstin sellaisenaan tai heittää virheen, jos jokin avain puuttuu.
  Estää kirjoitusvirheen muuttumisen hiljaa nil-arvoksi kenttäkuvauksessa."
  [konteksti]
  (let [puuttuvat (vec (remove #(contains? konteksti %) nakymakontekstin-avaimet))]
    (when (seq puuttuvat)
      (throw (ex-info (str "Sanktiolomakkeen kontekstista puuttuu avaimet: " (pr-str puuttuvat))
               {:puuttuvat puuttuvat})))
    konteksti))

;; Hoitovuosi tunnistetaan sen alkuvuodella (hoitovuosi alkaa 1.10.).
(defn hoitovuodet
  "Urakan hoitovuosien alkuvuodet urakan alkuvuodesta viimeiseen urakan loppupäivään osuvaan hoitovuoteen."
  [urakan-alkuvuosi urakan-loppupvm]
  (if-not urakan-loppupvm
    []
    (let [viimeinen (if (>= (pvm/kuukausi urakan-loppupvm) 10)
                    (pvm/vuosi urakan-loppupvm)
                    (dec (pvm/vuosi urakan-loppupvm)))]
      (vec (range urakan-alkuvuosi (inc viimeinen))))))

(defn hoitovuosi-tekstina
  [urakan-alkuvuosi hoitovuosi]
  (when (int? hoitovuosi)
    (str (inc (- hoitovuosi urakan-alkuvuosi)) ". hoitovuosi (" hoitovuosi " - " (inc hoitovuosi) ")")))

(defn perintapvm-hoitovuodeksi
  "Palauttaa perintäpäivän hoitovuoden alkuvuoden, jos se kuuluu annettuihin hoitovuosiin."
  [hoitovuodet perintapvm]
  (when perintapvm
    (let [hoitovuosi (pvm/hoitokauden-alkuvuosi (pvm/vuosi perintapvm) (pvm/kuukausi perintapvm))]
      (when (some #{hoitovuosi} hoitovuodet)
        hoitovuosi))))

(defn hoidonjohtopalkkio-tehtavaryhma
  "MHU25+ sanktio kohdistuu tehtäväryhmään G - Hoidonjohtopalkkio."
  [tehtavaryhmat]
  (some #(when (= "G - Hoidonjohtopalkkio" (:tehtavaryhma_nimi %)) %) tehtavaryhmat))

(defn aseta-kasittely-tai-maarattypvm
  [sanktio arvo perinta-tapa]
  (let [maarattypvm? (= :maarattypvm perinta-tapa)
        perintapvm (when-not (:laskutuskuukausi-komp-tiedot sanktio)
                     (if maarattypvm?
                       (when arvo
                         (pvm/hoitokauden-loppupvm
                           (pvm/vuosi
                             (second (pvm/paivamaaran-hoitokausi arvo)))))
                       arvo))]
    (assoc-in
      (cond-> (assoc sanktio :maarattypvm arvo)
        (nil? (:laskutuskuukausi-komp-tiedot sanktio))
        (assoc :perintapvm perintapvm))
      [:laatupoikkeama :paatos :kasittelyaika]
      arvo)))

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
    (if (and (not (sanktio-domain/muu-kuin-muistutus? rivi))
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
        kiintea-summa (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa uusi-tyyppi)
        manuaalinen-c? (some? (summamaaritys/c-ryhman-manuaalinen-maaritys (:laji rivi) uusi-tyyppi))
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
        :summa (summamaaritys/kiintea-sanktiosumma kiintea-summa omailmoitettu))
      (and tyyppi-vaihtui? manuaalinen-c?)
      (dissoc :normaalimaara :summa)
      (and tyyppi-vaihtui? (nil? kiintea-summa) (not manuaalinen-c?))
      (as-> rivi
        (cond-> rivi
          (contains? rivi :normaalimaara) (assoc :normaalimaara nil)
          (contains? rivi :summa) (assoc :summa nil))))))

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
        perusteluton? (sanktiotyyppi-domain/perusteluton-sanktio? laji tyyppi)]
    {:vesivaylaurakka? vesivaylaurakka?
     :yllapitourakka? yllapitourakka?
     :laskutus-ilman-laskutuskelpoisuutta? laskutus-ilman-laskutuskelpoisuutta?
     :nayta-tyyppi? (not (or yllapitourakka? vesivaylaurakka?
                           asiakirjamerkinta?
                           laskutus-ilman-laskutuskelpoisuutta?
                           laskutusrajan-ylitys?))
     :nayta-tapahtumapaikka? (not (or yllapitokohdeurakka? vesivaylaurakka?
                                    laskutus-ilman-laskutuskelpoisuutta?
                                    laskutusrajan-ylitys?))
     :nayta-havaittu? (not laskutus-ilman-laskutuskelpoisuutta?)
     :nayta-laskutuskuukausi-teksti? (not yllapitourakka?)
     :nayta-indeksi? (and (:nayta-indeksi? aikakausikonteksti)
                       (not= :muistutus laji))
     :nayta-manuaalinen-summa? (and (not= :muistutus laji)
                                 (not= :laskutus_yli_laskutusrajan laji))
     :nayta-perustelu? (not perusteluton?)
     :nayta-puute-tai-laiminlyonti? yllapitourakka?
     :nayta-talvisuola-ilmoitus? (= :talvisuolan_ylitys laji)
     :salli-arvonvahennys? (:salli-arvonvahennys? aikakausikonteksti)
     :talvisuolan-validointi (fn [arvo]
                               (talvisuolan-validointi urakan-loppupvm arvo sanktio))}))
