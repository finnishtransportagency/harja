(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma
  (:require [taoensso.timbre :as log]
            [clj-time.format :as df])
  (:import (org.joda.time DateTime)
           (java.sql Timestamp)))

; Varusteiden nimikkeistö
; TL 501 Kaiteet
; HUOMIO Mikään ei erota melurakenteiden kaiteita tavallisista kaiteista.
; TL 503 Levähdysalueiden varusteet
(def +poyta-ja-penkki+ "tienvarsikalustetyyppi/tvkt03")
(def +eko-kierratyspiste+ "tienvarsikalustetyyppi/tvkt06")
(def +kemiallisen-wc_n-tyhjennyspiste+ "tienvarsikalustetyyppi/tvkt07")
(def +leikkialue+ "tienvarsikalustetyyppi/tvkt12")
(def +kuntoiluvaline+ "tienvarsikalustetyyppi/tvkt13")
(def +katos+ "tienvarsikalustetyyppi/tvkt02")
(def +laituri+ "tienvarsikalustetyyppi/tvkt19")
(def +pukukoppi+ "tienvarsikalustetyyppi/tvkt14")
(def +opastuskartta+ "tienvarsikalustetyyppi/tvkt15")
(def +tulentekopaikka+ "tienvarsikalustetyyppi/tvkt16")
(def +polkupyorakatos+ "tienvarsikalustetyyppi/tvkt27")
; #ext-urpo
; Petri Sirkkala  7 days ago
;@Erkki Mattila En löydä Latauspalvelun tienvarsikalusteet.(nd)json tiedostosta yhtään tvkt27:aa.
; Sensijaan tvkt17 ilmenee 152 kertaa.
;
;Erkki Mattila  6 days ago
;[...] Tuossa tosiaan oli joku semmonen juttu muistaakseni, että tierekkarista ei pystynyt päättelemään,
; että onko kyseessä teline vai katos, niin ne kaikki on telineitä nyt
;
; Myöhemmin todettu, että kaikki (def +polkupyorateline+ "tienvarsikalustetyyppi/tvkt17") ovat TL 507 bussipysäkin varusteita

(def +tl503-ominaisuustyyppi-arvot+ #{+poyta-ja-penkki+
                                      +eko-kierratyspiste+
                                      +kemiallisen-wc_n-tyhjennyspiste+
                                      +leikkialue+
                                      +kuntoiluvaline+
                                      +katos+
                                      +laituri+
                                      +pukukoppi+
                                      +opastuskartta+
                                      +tulentekopaikka+
                                      +polkupyorakatos+})
; TL 504 WC
(def +wc+ "tienvarsikalustetyyppi/tvkt11")
; TL 505 Jätehuolto
(def +maanpaallinen-jateastia-alle-240-l+ "tienvarsikalustetyyppi/tvkt08")
(def +maanpaallinen-jatesailio-yli-240-l+ "tienvarsikalustetyyppi/tvkt09")
(def +upotettu-jatesailio+ "tienvarsikalustetyyppi/tvkt10")
(def +tl505-ominaisuustyyppi-arvot+ #{+maanpaallinen-jateastia-alle-240-l+
                                      +maanpaallinen-jatesailio-yli-240-l+
                                      +upotettu-jatesailio+})
; TL 507 Bussipysäkin varusteet
(def +roska-astia+ "tienvarsikalustetyyppi/tvkt05")
(def +polkupyorateline+ "tienvarsikalustetyyppi/tvkt17")
(def +aikataulukehikko+ "tienvarsikalustetyyppi/tvkt20")
(def +penkki+ "tienvarsikalustetyyppi/tvkt04")
(def +tl507-ominaisuustyyppi-arvot+ #{+roska-astia+
                                      +polkupyorateline+
                                      +aikataulukehikko+
                                      +penkki+})
; TL 508 Bussipysäkin katos
(def +bussipysakin-katos+ "tienvarsikalustetyyppi/tvkt01")
; TL 514 Melurakenteet
(def +sisa-tai-ulkoluiska+ "luiska-tyyppi/luity03")
; TL 516 Hiekkalaatikot
(def +hiekkalaatikko+ "tienvarsikalustetyyppi/tvkt18")
; TL 518 Kivetyt alueet
(def +liikennesaareke+ "erotusalue-tyyppi/erty05")
(def +korotettu-erotusalue+ "erotusalue-tyyppi/erty02")
(def +bussipysakin-odotusalue+ "erotusalue-tyyppi/erty07")
(def +sisaluiska+ "luiska-tyyppi/luity01")
(def +tl518_ominaisuustyyppi-arvot+ #{+liikennesaareke+
                                      +korotettu-erotusalue+
                                      +bussipysakin-odotusalue+})
(defn sql->aika
  "Luo org.joda.time.DateTime objektin annetusta java.sql.Timestamp objektista.
  Käyttää UTC aikavyöhykettä.
  Paluttaa nil, jos saa nil."
  [^Timestamp dt]
  (when dt
    (clj-time.coerce/from-sql-time dt)))

(defn aika->sql
  "Luo java.sql.Timestamp objektin org.joda.time.DateTime objektista.
   Käyttää UTC aikavyöhykettä.
   Paluttaa nil, jos saa nil."
  [^DateTime dt]
  (when dt
    (clj-time.coerce/to-sql-time dt)))

(defn aika->velho-aika
  [aika]
  (when aika
    (let [parempi-aika (cond (instance? DateTime aika) aika
                             (instance? Timestamp aika) (sql->aika aika)
                             :else (throw (IllegalArgumentException.
                                            "aika->velho-aika: aika pitää olla org.joda.time.DateTime tai java.sql.Timestamp")))]
      (df/unparse (:date-time-no-ms df/formatters) parempi-aika))))

(defn velho-aika->aika
  "Muuttaa Velhon pvm muotoisen tekstin org.joda.time.DateTime muotoon.  Paluttaa nil, jos saa nil."
  [teksti]
  (when teksti
    (df/parse (:date-time-no-ms df/formatters) teksti)))

(defn velho-pvm->pvm
  "Muuttaa Velhon pvm tekstin org.joda.time.DateTime muotoon.  Paluttaa nil, jos saa nil."
  [^String teksti]
  (when teksti
    (df/parse (:date df/formatters) teksti)))


(defn filter-by-vals [pred m] (into {} (filter (fn [[k v]] (pred v)) m)))

(defn varusteen-tietolaji [kohde]
  (let [kohdeluokka (:kohdeluokka kohde)
        ominaisuudet (:ominaisuudet kohde)
        rakenteelliset-ominaisuudet (:rakenteelliset-ominaisuudet ominaisuudet)
        rakenteelliset-jarjestelmakokonaisuudet (get-in ominaisuudet [:infranimikkeisto :rakenteellinen-jarjestelmakokonaisuus])
        melurakenne? (and false rakenteelliset-jarjestelmakokonaisuudet)
        tl-map {:tl501 (and (= kohdeluokka "varusteet/kaiteet")
                            (not melurakenne?))
                :tl503 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl503-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl504 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +wc+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl505 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl505-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl506 (= kohdeluokka "varusteet/liikennemerkit")
                :tl507 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (contains? +tl507-ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl508 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +bussipysakin-katos+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl509 (= kohdeluokka "varusteet/rumpuputket")
                :tl512 (= kohdeluokka "varusteet/kaivot")
                :tl513 (= kohdeluokka "varusteet/reunapaalut")
                :tl514 (and (= kohdeluokka "tiealueen-poikkileikkaus/luiskat")
                            (= +sisa-tai-ulkoluiska+ (:tyyppi ominaisuudet)))
                :tl515 (= kohdeluokka "varusteet/aidat")
                :tl516 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +hiekkalaatikko+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl517 (= kohdeluokka "varusteet/portaat")
                :tl518 (or (and (= kohdeluokka "tiealueen-poikkileikkaus/erotusalueet")
                                (contains? +tl518_ominaisuustyyppi-arvot+ (:tyyppi ominaisuudet)))
                           (and (= kohdeluokka "tiealueen-poikkileikkaus/luiskat")
                                (= +sisaluiska+ (:tyyppi ominaisuudet))))
                ; Huom. myös Rautatietasoristeyksen puomit (TL192) ovat TL520. Ne elävät kaksoiselämää.
                :tl520 (= kohdeluokka "varusteet/puomit-sulkulaitteet-pollarit")
                :tl522 (= kohdeluokka "varusteet/reunatuet")
                :tl524 (= kohdeluokka "ymparisto/viherkuviot")}
        tl-keys (keys (filter-by-vals identity tl-map))]
    (cond
      (> (count tl-keys) 1) (do (log/error (str "Varustekohteen tietolaji ei ole yksikäsitteinen. oid: "
                                                (:oid kohde) " tietolajit: "
                                                tl-keys ""))
                                nil)
      (= 0 (count tl-keys)) nil
      :else (name (first tl-keys)))))

(defn puuttuvat-pakolliset-avaimet [varustetoteuma2]
  (let [pakolliset (dissoc varustetoteuma2 :tr_loppuosa :tr_loppuetaisyys :lisatieto :loppupvm)
        puuttuvat-avaimet (->> pakolliset
                               (filter #(nil? (val %)))
                               (map first)
                               vec)]
    puuttuvat-avaimet))

(defn validointi-viesti [puuttuvat-pakolliset-avaimet]
  (str "Puuttuu pakollisia avaimia: " puuttuvat-pakolliset-avaimet))

(defn varusteen-lisatieto [konversio-fn tietolaji kohde]
  (when (= "tl506" tietolaji)
    (konversio-fn
      "v/vtlm" (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :asetusnumero]))))

(defn varusteen-kuntoluokka [konversio-fn kohde]
  (let [kuntoluokka (get-in kohde [:ominaisuudet :kunto-ja-vauriotiedot :yleinen-kuntoluokka])]
    (if kuntoluokka
      (konversio-fn "v/vtykl" kuntoluokka)
      "Puuttuu")))

(defn varusteen-toteuma [{:keys [alkaen version-voimassaolo uusin-versio] :as kohde}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)]
    (cond (= alkaen version-alku) "lisatty"
          (and uusin-versio (some? version-loppu)) "poistettu"
          :else "paivitetty")))

(defn velho->harja
  "Muuttaa Velhosta saadun varustetiedon Harjan varustetoteuma2 muotoon."
  [urakka-id-kohteelle-fn sijainti-kohteelle-fn konversio-fn kohde]
  (let [alkusijainti (or (:sijainti kohde) (:alkusijainti kohde))
        loppusijainti (:loppusijainti kohde)                ;voi olla nil
        tietolaji (varusteen-tietolaji kohde)               ;voi olla nil
        varustetoteuma2 {:velho_oid (:oid kohde)
                         :urakka_id (urakka-id-kohteelle-fn kohde)
                         :tr_numero (:tie alkusijainti)
                         :tr_alkuosa (:osa alkusijainti)
                         :tr_alkuetaisyys (:etaisyys alkusijainti)
                         :tr_loppuosa (:osa loppusijainti)
                         :tr_loppuetaisyys (:etaisyys loppusijainti)
                         :sijainti (sijainti-kohteelle-fn kohde)
                         :tietolaji tietolaji
                         :lisatieto (varusteen-lisatieto konversio-fn tietolaji kohde)
                         :toteuma (varusteen-toteuma kohde)
                         :kuntoluokka (varusteen-kuntoluokka konversio-fn kohde)
                         :alkupvm (-> kohde
                                      (get-in [:version-voimassaolo :alku])
                                      velho-pvm->pvm
                                      aika->sql)
                         :loppupvm (when-let [loppupvm (get-in kohde [:version-voimassaolo :loppu])]
                                     (-> loppupvm
                                         velho-pvm->pvm
                                         aika->sql))
                         :muokkaaja (get-in kohde [:muokkaaja :kayttajanimi])
                         :muokattu (-> kohde
                                       :muokattu
                                       velho-aika->aika
                                       aika->sql)}
        puuttuvat-pakolliset-avaimet (puuttuvat-pakolliset-avaimet varustetoteuma2)]
    (if (empty? puuttuvat-pakolliset-avaimet)
      {:tulos varustetoteuma2 :tietolaji tietolaji :virheviesti nil}
      {:tulos nil :tietolaji tietolaji :virheviesti (str "validointivirhe: " (validointi-viesti puuttuvat-pakolliset-avaimet))})))
