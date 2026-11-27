(ns harja.ui.perus.valit-test
  "Välisääntö: shared-komponentit eivät aseta ulkomarginaaleja, vaan lasten välin omistaa vanhempi
  `gap`illa. Sääntö on kirjattu `komponentit.less`-tiedoston alkuun."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private less-tiedosto "dev-resources/less/komponentit.less")
(def ^:private perus-tiedosto "src/cljc/harja/ui/perus.cljc")
(def ^:private valit [:pieni :keski :suuri])
(def ^:private sisalto-wrapperit ["ui-osio-sisalto" "ui-kortti-sisalto"])

(defn- ilman-kommentteja [teksti]
  (-> teksti
    (str/replace #"(?s)/\*.*?\*/" "")
    (str/replace #"(?m)//.*$" "")))

(defn- saannot
  "Yksinkertaiset `valitsin { ilmoitukset }` -lohkot. Sisäkkäisistä lohkoista tunnistetaan vain
  sisimmät, joten komponenttien juurivalitsimet kirjoitetaan litteinä."
  []
  (for [[_ valitsin runko] (re-seq #"([^{};]+)\{([^{}]*)\}" (ilman-kommentteja (slurp less-tiedosto)))]
    {:valitsimet (map str/trim (str/split valitsin #","))
     :ilmoitukset (into {}
                    (keep (fn [rivi]
                            (let [[avain arvo] (map str/trim (str/split rivi #":" 2))]
                              (when (and (not (str/blank? avain)) arvo)
                                [avain arvo]))))
                    (str/split runko #";"))}))

(defn- saannot-valitsimelle [valitsin]
  (filter #(some #{valitsin} (:valitsimet %)) (saannot)))

(defn- juuriluokat
  "Luokat, jotka `harja.ui.perus` asettaa komponenttien juurielementeille."
  []
  (->> (re-seq #"\(luokat\s+\"(ui-[\w-]+)\"" (slurp perus-tiedosto))
    (map second)
    distinct))

(defn- ulkomarginaali?
  [[avain arvo]]
  (and (str/starts-with? avain "margin")
    (not (every? #{"0" "auto"} (str/split arvo #"\s+")))))

(deftest juurielementeilla-ei-ole-ulkomarginaalia
  (let [luokat (juuriluokat)]
    (is (>= (count luokat) 10) "Juuriluokkien tunnistus toimii")
    (doseq [luokka luokat
            saanto (saannot-valitsimelle (str "." luokka))
            ilmoitus (filter ulkomarginaali? (:ilmoitukset saanto))]
      (is false (str "." luokka " asettaa ulkomarginaalin " ilmoitus
                  ". Väli kuuluu vanhemman gapille.")))))

(deftest sailion-sisalto-wrapperit-omistavat-lasten-valin
  (doseq [wrapper sisalto-wrapperit]
    (testing wrapper
      (let [ilmoitukset (apply merge (map :ilmoitukset (saannot-valitsimelle (str "." wrapper))))]
        (is (= "flex" (get ilmoitukset "display")))
        (is (= "column" (get ilmoitukset "flex-direction")))
        (is (contains? ilmoitukset "gap"))))))

(deftest osion-vali-token-ohjaa-sisallon-lasten-valia
  (doseq [vali valit
          :let [valitsin (str ".ui-osio-vali-" (name vali) " > .ui-osio-sisalto")]]
    (is (some #(contains? (:ilmoitukset %) "gap") (saannot-valitsimelle valitsin))
      (str valitsin " puuttuu"))))

(deftest pinon-vali-token-asettaa-gapin
  (doseq [vali valit
          :let [valitsin (str ".ui-pino-vali-" (name vali))]]
    (is (some #(contains? (:ilmoitukset %) "gap") (saannot-valitsimelle valitsin))
      (str valitsin " puuttuu"))))
