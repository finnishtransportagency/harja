(ns harja-laadunseuranta.kuvat
  (:require-macros [harja-laadunseuranta.inline-kuva :refer [inline-svg]]))

(defn- ikoni [nimi]
  (str "img/" nimi))

(def +autonuoli+ (ikoni "nuoli.svg"))
(def +harja-logo+ (ikoni "harja_logo_soft.svg"))
(def +harja-logo-ilman-tekstia+ (ikoni "harja_logo_soft_ilman_tekstia.svg"))
(def +kamera+ (ikoni "kamera.png"))
(def +info+ (ikoni "info.svg"))
(def +check+ (ikoni "check.svg"))
(def +cross+ (ikoni "cross.svg"))
(def +kiinteisto+ (ikoni "mokki.svg"))
(def +spinner+ (ikoni "ajax-loader.gif"))
(def +avattu+ (ikoni "avattu.png"))
(def +keskityspainike+ (ikoni "keskitys.png"))
(def +havaintopiste+ (ikoni "havaintopiste.png"))


(def paallystys-tyovirheet
  {:saumavirhe (inline-svg "resources/public/laadunseuranta/img/tyovirheet/saumavirhe-24.svg")
   :lajittuma (inline-svg "resources/public/laadunseuranta/img/tyovirheet/lajittuma-24.svg")
   :epatasaisuus (inline-svg "resources/public/laadunseuranta/img/tyovirheet/epatasa-24.svg")
   :halkeamat (inline-svg "resources/public/laadunseuranta/img/tyovirheet/halkeama-24.svg")
   :vesilammikot (inline-svg "resources/public/laadunseuranta/img/tyovirheet/vesilammikko-24.svg")
   :epatasaisetreunat (inline-svg "resources/public/laadunseuranta/img/tyovirheet/epatasaiset-reunat-24.svg")
   :jyranjalkia (inline-svg "resources/public/laadunseuranta/img/tyovirheet/jyran-jalki-24.svg")
   :sideainelaikkia (inline-svg "resources/public/laadunseuranta/img/tyovirheet/laikka-24.svg")
   :vaarakorkeusasema (inline-svg "resources/public/laadunseuranta/img/tyovirheet/vaara-korkeus-24.svg")
   :pintaharva (inline-svg "resources/public/laadunseuranta/img/tyovirheet/harva-pinta-24.svg")
   :pintakuivatuspuute (inline-svg "resources/public/laadunseuranta/img/tyovirheet/pintakuivatus-puute-24.svg")
   :kaivojenkorkeusasema (inline-svg "resources/public/laadunseuranta/img/tyovirheet/kaivon-korkeus-24.svg")})
