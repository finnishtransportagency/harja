(ns harja-laadunseuranta.tiedot.asetukset.asetukset
  (:require [clojure.string :as s]
            [harja-laadunseuranta.utils :as utils]))

(defn- prefix []
  (if (utils/kehitysymparistossa?)
    ""
    "harja/"))

(def +wmts-url+ (str "/" (prefix) "wmts/maasto/wmts"))
(def +wmts-url-kiinteistojaotus+ (str "/" (prefix) "wmts/kiinteisto/wmts"))
(def +wmts-url-ortokuva+ (str "/" (prefix) "wmts/maasto/wmts"))

;; reitintallennus
(def +reittimerkinta-store+ "reittimerkinnat")
(def +tarkastusajo-store+ "tarkastusajo")
(def +pollausvali+ 2000)
(def +tallennus-url+ (str "/" (prefix) "_/ls-reittimerkinta"))
(def +paatos-url+ (str "/" (prefix) "_/ls-paata-tarkastusajo"))
(def +luonti-url+ (str "/" (prefix) "_/ls-uusi-tarkastusajo"))
(def +simuloitu-ajo-url+ (str "/" (prefix) "_/ls-simuloitu-reitti"))
(def +tr-tietojen-haku-url+ (str "/" (prefix) "_/ls-hae-tr-tiedot"))
(def +kayttajatiedot-url+ (str "/" (prefix) "_/ls-hae-kayttajatiedot"))
(def +liitteen-tallennus-url+ (str "/" (prefix) "_/ls-tallenna-liite"))
(def +urakkatyypin-urakat-url+ (str "/" (prefix) "_/ls-urakkatyypin-urakat"))

(def +persistoitavien-max-maara+ 500)

;; kartta
(def +oletuszoom+ 14) ;; Väliltä 1-18 (arvo 1 on erittäin kaukana ja arvo 18 on ihan kiinni tiessä)
(def +min-zoom+ 12.6)
(def +max-zoom+ 14)
(def +heading-ikonikorjaus+ -90)
(def +reittiviivan-leveys+ 4)
;; Montako sekuntia kartan annetaan olla käyttäjän zoomaamassa paikassa
;; ennen kuin automatiikka palaa käyttöön
(def +kunnioita-kayttajan-zoomia-s+ 30)

;; Tätä epätarkempia pisteitä ei koskaan kirjata IndexedDB:n eikä Harjaan.
;; Asetettu mielekkääksi tutkimalla seuraavien laitteiden keskimääräiset GPS-tarkkuudet
;; pikaisella testillä:
;; - Samsung Galaxy S4: 10
;; - Samsung Galaxy Tab A: 23
;; - Apple iPhone: 60
(def +suurin-sallittu-tarkkuus+ 80) ;; Metreinä, mitä pienempi, sitä tarkempi

(def +tros-haun-treshold+ 100)

;; paluu harjaan
(def +harja-url+ (str "/" (prefix) "#urakat/laadunseuranta/"))

;; Ilmoitukset
(def +ilmoituksen-nakymisaika-ms+ 10000)