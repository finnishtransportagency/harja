(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma
  (:require [taoensso.timbre :as log]
            [clj-time.format :as df]
            [harja.pvm :as pvm]
            [harja.geo :as geo]
            [clojure.string :as str])
  (:import (org.joda.time DateTime)
           (java.sql Timestamp)))


(def +kaikki-tietolajit+ #{:tl501 :tl503 :tl504 :tl505 :tl506 :tl507 :tl508 :tl509 :tl512
                           :tl513 :tl514 :tl515 :tl516 :tl517 :tl518 :tl520 :tl522 :tl524})
(def +liikennemerkki-tietolaji+ :tl506)

(def +tieosoitemuutos-nimikkeistoarvo+ :tt01)
(def +muu-tekinen-toimenpide-nimikkeistoarvo+ :tt02)

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
                +liikennemerkki-tietolaji+ (= kohdeluokka "varusteet/liikennemerkit")
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

(defn puuttuvat-pakolliset-avaimet [varustetoteuma]
  (let [pakolliset (dissoc varustetoteuma :tr_loppuosa :tr_loppuetaisyys :lisatieto :loppupvm)
        puuttuvat-avaimet (->> pakolliset
                               (filter #(nil? (val %)))
                               (map first)
                               vec)]
    puuttuvat-avaimet))

(defn aikavalit-leikkaavat [{a-alku :alkupvm a-loppu :loppupvm :as vali1} {b-alku :alkupvm b-loppu :loppupvm :as vali2}]
  (let [minus-infinity (pvm/->pvm "1.1.1900")
        plus-infinity (pvm/->pvm "1.1.2200")
        a-alku* (or a-alku minus-infinity)
        a-loppu* (or a-loppu plus-infinity)
        b-alku* (or b-alku minus-infinity)
        b-loppu* (or b-loppu plus-infinity)]
    (pvm/aikavalit-leikkaavat? a-alku* a-loppu* b-alku* b-loppu*)))

(defn tarkasta-varustetoteuma
  "Tarkistaa varusteversion oikeellisuuden ja palauttaa toimintavaihtoehdot:
  :tallenna, :ohita, :varoita

  Tarkistukset:
  1. pakolliset kentät
  2. muutoksen-lahde-oid pitää olla Hallintorekisterin maanteiden-hoitourakka, jonka urakkakoodi vastaa VHAR-6045 mukaisesti Harjassa olevaan hoito
     tai teiden-hoito tyyppisen voimassaolevan urakan tunnisteeseen (URAKKA.urakkanro)
  3. version-voimassaolon alkupvm pitää leikata 1. kohdan urakan keston kanssa. (Jos näin ei ole, ei kohde näy käyttöliittymässä.)
  4. Varusteen tietolajin pitää sisältyä VHAR-5109 kommentissa mainittuihin tietolajeihin
  5. Varusteversion toimenpiteen pitää olla jokin seuraavista: lisäys, päivitys, poisto, tarkastus, korjaus ja puhdistus
  6. Varusten ollessa tl506 (liikennemerkki) tulee sillä olla asetusnumero tai lakinumero, joka kertoo liikennemerkin tyypin
     (meillä lisätieto-tekstiä)
  7. Varusteversion versioitu.tekninen-tapatuma tulee olla tyhjä

  Tulokset:
  2b. 4. 6. ja 7. -> :ohita
  1. 3. 5. ja 2a -> :varoita
  muuten :tallenna.

  Varoitus jättää tämän lähteen viimeisen ajokerran päiväyksen päivittämättä, eli integraatio epäonnistuu osittain.
  Tästä seuraa uudelleen lataus samasta alkupäivämäärästä lähtien seuraavalla ajokerralla."
  [{:keys [alkupvm loppupvm urakka_id] :as varustetoteuma} urakka-olemassaolo muutoksen-lahde-oid]

  (let [; VHAR-6330 Version alkupvm on oltava urakan sisällä, muuten versio ei ole syntynyt urakassa
        varuste-olemassaolo {:alkupvm alkupvm :loppupvm alkupvm}
        puuttuvat-pakolliset (puuttuvat-pakolliset-avaimet varustetoteuma)]
    (cond
      ; 2a
      (and (nil? urakka_id) (not= "migraatio" (:muokkaaja varustetoteuma)) )
      {:toiminto :varoita :viesti (format "Muutoksen lähteen %s urakkaa ei löydy Harjasta ja muokkaaja on joku muu kuin 'migraatio'. Pyydä Velhoa lisäämään urakka-id varusteelle" muutoksen-lahde-oid)}
      ; 2b
      (nil? urakka_id)
      {:toiminto :ohita :viesti (format "Muutoksen lähteen %s urakkaa ei löydy Harjasta. Ohita varustetoteuma." muutoksen-lahde-oid)}
      ; 4
      (not (contains? +kaikki-tietolajit+ (keyword (:tietolaji varustetoteuma))))
      {:toiminto :ohita :viesti "Tietolaji ei vastaa Harjan valittuja tietojajeja. Ohita varustetoteuma."}
      ; 6
      (and (= (name +liikennemerkki-tietolaji+) (:tietolaji varustetoteuma)) (str/blank? (:lisatieto varustetoteuma)))
      {:toiminto :ohita :viesti "Liikennemerkin lisätieto puuttuu. Ohita varustetoteuma."}
      ; 7a
      (= (name +tieosoitemuutos-nimikkeistoarvo+) (:toteuma varustetoteuma))
      {:toiminto :ohita :viesti "Tekninen toimenpide: Tieosoitemuutos. Ohita varustetoteuma."}
      ; 7b
      (= (name +muu-tekinen-toimenpide-nimikkeistoarvo+) (:toteuma varustetoteuma))
      {:toiminto :ohita :viesti "Tekninen toimenpide: Muu tekninen toimenpide. Ohita varustetoteuma."}
      ; Pakollisuudet viimeisenä, koska ohitaminen pitää tehdä ensin ettei tule virheilmoituksia
      ; sellaisista, jotka eivät Harjaan kuulu
      ; 3
      (not (aikavalit-leikkaavat varuste-olemassaolo urakka-olemassaolo))
      {:toiminto :varoita :viesti
       (str "version-voimassaolon alkupvm: " alkupvm " pitää sisältyä urakan aikaväliin. "
            "Urakan id: " urakka_id " voimassaolo: {:alkupvm " (:alkupvm urakka-olemassaolo) " :loppupvm " (:loppupvm urakka-olemassaolo) "}")}
      ; 5
      (nil? (:toteuma varustetoteuma))
      {:toiminto :varoita :viesti "Toimenpide ei ole lisäys, päivitys, poisto, tarkastus, korjaus tai puhdistus"}
      ; 1
      (seq puuttuvat-pakolliset)
      {:toiminto :varoita :viesti (str "Puuttuu pakollisia kenttiä: " puuttuvat-pakolliset)}

      :else                                                 ; Jos kaikki on ok, päätetään tallentaa varuste.
      {:toiminto :tallenna :viesti nil})))

(defn varusteen-lisatieto [konversio-fn tietolaji kohde]
  (when (= (name +liikennemerkki-tietolaji+) tietolaji)
    (let [asetusnumero (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :asetusnumero])
          lakinumero (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :lakinumero])
          lisatietoja (get-in kohde [:ominaisuudet :toiminnalliset-ominaisuudet :lisatietoja])
          merkki (cond
                   (and asetusnumero (nil? lakinumero))
                   (str (konversio-fn "v/vtlm" asetusnumero kohde))

                   (and (nil? asetusnumero) lakinumero)
                   (konversio-fn "v/vtlmln" lakinumero kohde)

                   (and (nil? asetusnumero) (nil? lakinumero))
                   "VIRHE: Liikennemerkin asetusnumero ja lakinumero tyhjiä Tievelhossa"

                   (and asetusnumero lakinumero)
                   "VIRHE: Liikennemerkillä sekä asetusnumero että lakinumero Tievelhossa")]
      (if lisatietoja
        (str merkki ": " lisatietoja)
        merkki))))

(defn varusteen-kuntoluokka [konversio-fn kohde]
  (let [kuntoluokka (get-in kohde [:ominaisuudet :kunto-ja-vauriotiedot :yleinen-kuntoluokka])]
    (if kuntoluokka
      (konversio-fn "v/vtykl" kuntoluokka kohde)
      "Puuttuu")))

(defn varusteen-toteuma [konversio-fn {:keys [version-voimassaolo alkaen paattyen uusin-versio ominaisuudet tekninen-tapahtuma] :as kohde}]
  (let [version-alku (:alku version-voimassaolo)
        version-loppu (:loppu version-voimassaolo)
        toimenpiteet (:toimenpiteet ominaisuudet)
        toimenpidelista (->> toimenpiteet
                             (map #(konversio-fn "v/vtp" % kohde))
                             (keep not-empty))]
    (cond (< 1 (count toimenpidelista))
          (do
            ; Kuvittelemme, ettei ole kovin yleistä, että yhdessä
            ; varusteen versiossa on monta toimenpidettä
            (log/warn (str "Löytyi varusteversio, jolla on monta toimenpidettä: oid: " (:ulkoinen-oid kohde)
                           " version-alku: " version-alku " toimenpiteet(suodatettu): (" (str/join ", " (map #(str "\"" % "\"") toimenpidelista))
                           ") Otimme vain 1. toimenpiteen talteen."))
            (first toimenpidelista))

          (= 1 (count toimenpidelista))
          (first toimenpidelista)

          (= 0 (count toimenpidelista))
          ; Varusteiden lisäys, poisto ja muokkaus eivät ole toimenpiteitä Velhossa. Harjassa ne ovat.
          (cond (= "tekninen-tapahtuma/tt01" tekninen-tapahtuma) "tt01" ; Tieosoitemuutos
                (= "tekninen-tapahtuma/tt02" tekninen-tapahtuma) "tt02" ; Muu tekninen toimenpide
                (and (nil? version-voimassaolo) paattyen) "poistettu" ;Sijaintipalvelu ei palauta versioita
                (and (nil? version-voimassaolo) (not paattyen)) "lisatty"
                (= alkaen version-alku) "lisatty"           ; varusteen syntymäpäivä, onnea!
                (and uusin-versio (some? version-loppu)) "poistettu" ; uusimmalla versiolla on loppu
                :else "paivitetty"))))

(defn velhogeo->harjageo [geo]
  (let [tyyppi (get {"MultiLineString" :multiline
                     "MultiPoint" :multipoint
                     "LineString" :line
                     "Point" :point}
                    (:type geo))]
    (when geo
      (cond
        (= :point tyyppi)
        (-> {:coordinates (:coordinates geo) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :line tyyppi)
        (-> {:points (:coordinates geo) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :multiline tyyppi)
        (-> {:lines (map
                      (fn [p] {:type :line :points p})
                      (:coordinates geo)) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        (= :multipoint tyyppi)
        (-> {:coordinates (mapv
                      (fn [p] {:type :point :coordinates p})
                      (:coordinates geo)) :type tyyppi}
            (geo/clj->pg)
            (geo/geometry))

        :else
        (assert false (str "Tuntematon geometriatyyppi Velhosta: " geo))))))

(defn varustetoteuma-velho->harja
  "Muuttaa Velhosta saadun varustetiedon Harjan varustetoteuma muotoon.

  Palauttaa {:tulos varustetoteuma :tietolaji tietolaji :virheviesti nil}, jos onnistuu,
  {:tulos nil :tietolaji tietolaji :virheviesti (str \"validointivirhe: \" (validointi-viesti puuttuvat-pakolliset-avaimet))} muulloin."
  [urakka-id-kohteelle-fn sijainti-kohteelle-fn konversio-fn urakka-pvmt-idlla-fn kohde]
  (let [velho-pvm->sql (fn [teksti] (-> teksti
                                        velho-pvm->pvm
                                        aika->sql))
        alkusijainti (or (:sijainti kohde) (:alkusijainti kohde))
        loppusijainti (:loppusijainti kohde)                ;voi olla nil
        tietolaji (varusteen-tietolaji kohde)               ;voi olla nil
        alkupvm (or (-> kohde
                        (get-in [:version-voimassaolo :alku])
                        velho-pvm->sql)
                    (velho-pvm->sql (:alkaen kohde)))       ;Sijaintipalvelu ei palauta versioita
        loppupvm (when-let [loppupvm (get-in kohde [:version-voimassaolo :loppu])]
                   (-> loppupvm
                       velho-pvm->sql))
        muokattu (-> kohde
                     :muokattu
                     velho-aika->aika
                     aika->sql)
        klgeopg (velhogeo->harjageo (:keskilinjageometria kohde))

        varustetoteuma {:ulkoinen_oid (:oid kohde)
                        :urakka_id (urakka-id-kohteelle-fn kohde)
                        :tr_numero (:tie alkusijainti)
                        :tr_alkuosa (:osa alkusijainti)
                        :tr_alkuetaisyys (:etaisyys alkusijainti)
                        :tr_loppuosa (:osa loppusijainti)
                        :tr_loppuetaisyys (:etaisyys loppusijainti)
                        :sijainti (or
                                    klgeopg
                                    (sijainti-kohteelle-fn kohde)) ;tr-osoite fallbackina
                        :tietolaji tietolaji
                        :lisatieto (varusteen-lisatieto konversio-fn tietolaji kohde)
                        :toteuma (varusteen-toteuma konversio-fn kohde)
                        :kuntoluokka (varusteen-kuntoluokka konversio-fn kohde)
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :muokkaaja (get-in kohde [:muokkaaja :kayttajanimi])
                        :muokattu muokattu}
        urakka-olemassaolo (urakka-pvmt-idlla-fn (:urakka_id varustetoteuma))
        {toiminto :toiminto
         viesti :viesti} (tarkasta-varustetoteuma varustetoteuma urakka-olemassaolo (:muutoksen-lahde-oid kohde))]
    (cond
      (= :tallenna toiminto)                                ; <3
      {:tulos varustetoteuma :virheviesti nil :ohitusviesti nil}

      (= :ohita toiminto)                                 ; :|
      {:tulos nil :virheviesti nil :ohitusviesti viesti}

      (= :varoita toiminto)                                 ; :(
      {:tulos nil :virheviesti viesti :ohitusviesti nil})))
