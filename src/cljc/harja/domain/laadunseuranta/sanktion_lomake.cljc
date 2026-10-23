(ns harja.domain.laadunseuranta.sanktion-lomake
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]))

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

(defn kiintea-summamalli
  [{:keys [voi-puolittaa-omailmoituksella] :as sanktiotyyppi} omailmoitettu?]
  (when-let [normaalimaara (sanktiotyypin-kiintea-automaattinen-summa sanktiotyyppi)]
    (let [omailmoitettu (boolean (and voi-puolittaa-omailmoituksella omailmoitettu?))]
      {:normaalimaara normaalimaara
       :omailmoitettu omailmoitettu
       :summa (kiintea-sanktiosumma normaalimaara omailmoitettu)})))

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
     :nayta-perustelu? (not tyon-tekematta-jattamisen-tyyppi-22?)
     :nayta-puute-tai-laiminlyonti? yllapitourakka?
     :nayta-talvisuola-ilmoitus? (= :talvisuolan_ylitys laji)
     :salli-arvonvahennys? (:salli-arvonvahennys? aikakausikonteksti)
     :talvisuolan-validointi (fn [arvo]
                               (talvisuolan-validointi urakan-loppupvm arvo sanktio))}))
