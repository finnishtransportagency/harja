(ns harja.palvelin.integraatiot.velho.sanomat.varuste-vastaanottosanoma
  (:require [taoensso.timbre :as log]))

; Varusteiden nimikkeistö
; TL 501 Kaiteet
; TODO Mikä erottaa melurakenteiden kaiteet tavallisista kaiteista.
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
; TL 516 Hiekkalaatikot
(def +hiekkalaatikko+ "tienvarsikalustetyyppi/tvkt18")
; TL 518 Kivetyt alueet
(def +liikennesaareke+ "erotusalue-tyyppi/erty05")
(def +korotettu-erotusalue+ "erotusalue-tyyppi/erty02")
(def +bussipysakin-odotusalue+ "erotusalue-tyyppi/erty07")
(def +tl518_ominaisuustyyppi-arvot+ #{+liikennesaareke+
                                      +korotettu-erotusalue+
                                      +bussipysakin-odotusalue+})

(defn filter-by-vals [pred m] (into {} (filter (fn [[k v]] (pred v)) m)))

(defn varusteen-tietolaji [kohde]
  (let [kohdeluokka (:kohdeluokka kohde)
        rakenteelliset-ominaisuudet (get-in kohde [:ominaisuudet :rakenteelliset-ominaisuudet])
        rakenteelliset-jarjestelmakokonaisuudet (get-in kohde [:ominaisuudet :infranimikkeisto :rakenteellinen-jarjestelmakokonaisuus])
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
                :tl515 (= kohdeluokka "varusteet/aidat")
                :tl516 (and (= kohdeluokka "varusteet/tienvarsikalusteet")
                            (= +hiekkalaatikko+ (:tyyppi rakenteelliset-ominaisuudet)))
                :tl517 (= kohdeluokka "varusteet/portaat")
                :tl518 (or (and (= kohdeluokka "tiealueen-poikkileikkaus/erotusalueet")
                                (contains? +tl518_ominaisuustyyppi-arvot+ (:tyyppi rakenteelliset-ominaisuudet)))
                           (and (= kohdeluokka "tiealueen-poikkileikkaus/luiskat")
                                (= "luiska-tyyppi/luity01" (:tyyppi rakenteelliset-ominaisuudet))))
                :tl520 (= kohdeluokka "varusteet/puomit-sulkulaitteet-pollarit")
                :tl522 (= kohdeluokka "varusteet/reunatuet")
                :tl524 (= kohdeluokka "ymparisto/viherkuviot")}
        tl-keys (keys (filter-by-vals identity tl-map))]
    (cond
      (> 1 (count tl-keys)) (do (log/error
                                  (format "Varustekohteen tietolaji ole yksikäsitteinen. oid: %s tietolajit: %s"
                                          (:oid kohde)
                                          tl-keys))
                                nil)
      (= 0 (count tl-keys)) nil
      :else (name (first tl-keys)))))

(defn velho->harja                                          ; TODO Testi puuttuu
  "Muuttaa Velhosta saadun varustetiedon Harjan varustetoteuma2 muotoon."
  [urakka-id-kohteelle-fn sijainti-kohteelle-fn kohde]
  (let [alkusijainti (or (:sijainti kohde) (:alkusijainti kohde))
        loppusijainti (:loppusijainti kohde)                ;voi olla nil
        varustetoteuma2 {:velho_oid (:oid kohde)
                         :urakka_id (urakka-id-kohteelle-fn kohde)
                         :tr_numero (:tie alkusijainti)
                         :tr_alkuosa (:osa alkusijainti)
                         :tr_alkuetaisyys (:etaisyys alkusijainti)
                         :tr_loppuosa (:osa loppusijainti)
                         :tr_loppuetaisyys (:etaisyys loppusijainti)
                         :sijainti (sijainti-kohteelle-fn kohde)
                         :tietolaji (varusteen-tietolaji kohde)
                         :lisatieto (:a kohde)
                         :toimenpide "paivitetty"
                         :kuntoluokka 0
                         :alkupvm (harja.pvm/nyt)
                         :loppupvm nil
                         :muokkaaja "(:a kohde)"
                         :muokattu (harja.pvm/nyt)}]
    varustetoteuma2))
