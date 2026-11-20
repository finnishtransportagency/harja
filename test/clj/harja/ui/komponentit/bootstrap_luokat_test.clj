(ns harja.ui.komponentit.bootstrap-luokat-test
  "Rekisterin `:bootstrap-tila` ja `:bootstrap-luokat` pitää vastata komponentin funktion lähdekoodia."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.komponentit.rekisteri :as rekisteri]))

(def ^:private bootstrap-luokat
  "Bootstrapin luokat, joita `:ei-riippuvuutta`-komponentin funktio ei saa käyttää. Loppuviiva = etuliite."
  ["row" "col-" "form-group" "form-control" "form-horizontal" "panel" "panel-" "btn" "btn-"
   "dropdown" "dropdown-menu" "navbar" "navbar-" "nav-tabs" "nav-pills" "modal" "modal-dialog"
   "modal-content" "sr-only" "alert" "alert-" "glyphicon" "container-fluid"])

(defn- lahdetiedosto [ns-nimi]
  (let [polku (-> ns-nimi (str/replace "." "/") (str/replace "-" "_"))]
    (some (fn [[hakemisto paate]]
            (let [tiedosto (io/file (str hakemisto polku paate))]
              (when (.exists tiedosto) tiedosto)))
          [["src/cljs/" ".cljs"] ["src/cljc/" ".cljc"]])))

(defn- nimiavaruuden-lahde [funktio]
  (some-> (lahdetiedosto (first (str/split funktio #"/"))) slurp))

(defn- funktion-lahde
  "Funktion `(defn nimi ...)`-lomake, tai koko tiedosto jos funktiolla ei ole nimeä (esim. nimiavaruus)."
  [funktio]
  (let [[ns-nimi fn-nimi] (str/split funktio #"/")
        tiedosto (lahdetiedosto ns-nimi)
        teksti (some-> tiedosto slurp)]
    (when teksti
      (if-not fn-nimi
        teksti
        (let [alku (re-find (re-pattern (str "(?s)\\(defn-?\\s+" (java.util.regex.Pattern/quote fn-nimi) "[\\s\\n].*?(?=\\n\\((?:def|ns)|\\z)")) teksti)]
          alku)))))

(defn- luokka-kaytossa?
  "Luokka esiintyy luokkana: hiccup-avaimessa (`:div.luokka`) tai luokkalistan kaltaisessa merkkijonossa
  (`\"luokka\"`, `\"muu luokka\"`). Merkkijono, jossa on välimerkkejä (docstring), ja `:role`-arvo eivät ole luokkia."
  [lahde luokka]
  (let [q (java.util.regex.Pattern/quote luokka)
        loppu (if (str/ends-with? luokka "-") "" "(?![\\w-])")
        ilman-roolia (str/replace lahde #":role\s+\"[^\"]*\"" "")]
    (boolean (or (re-find (re-pattern (str "[\\w\\]\\.-]\\." q loppu)) ilman-roolia)
                 (re-find (re-pattern (str "\"\\s*(?:[\\w-]+\\s+)*" q (if (str/ends-with? luokka "-") "[\\w-]*" loppu) "(?:\\s+[\\w-]+)*\\s*\"")) ilman-roolia)))))

(deftest ilmoitetut-bootstrap-luokat-loytyvat-nimiavaruuden-lahdekoodista
  (testing "modal ilmoittaa käyttämänsä Bootstrap-luokat"
    (is (seq (:bootstrap-luokat (rekisteri/hae :modal)))))
  (doseq [komponentti (filter :bootstrap-luokat rekisteri/komponentit)
          :let [lahde (nimiavaruuden-lahde (:funktio komponentti))]
          luokka (:bootstrap-luokat komponentti)]
    (is (some? lahde) (str (:id komponentti) ": nimiavaruuden lähdekoodia ei löytynyt"))
    (is (and lahde (luokka-kaytossa? lahde luokka))
      (str (:id komponentti) ": luokkaa " luokka " ei löydy nimiavaruuden lähdekoodista"))))

(deftest ei-riippuvuutta-komponentti-ei-kayta-bootstrap-luokkia
  (doseq [komponentti (filter #(= :ei-riippuvuutta (:bootstrap-tila %)) rekisteri/komponentit)
          :when (str/includes? (:funktio komponentti) "/")
          :let [lahde (funktion-lahde (:funktio komponentti))]
          ;; Esim. defmulti/defmethod-toteutuksilla ei ole yksittäistä defn-lomaketta, joten niitä ei tarkisteta
          :when lahde]
    (doseq [luokka bootstrap-luokat]
      (is (not (and lahde (luokka-kaytossa? lahde luokka)))
        (str (:id komponentti) " on :ei-riippuvuutta mutta käyttää Bootstrap-luokkaa " luokka)))))

(deftest ei-riippuvuutta-ei-listaa-bootstrap-luokkia
  (is (empty? (filter (fn [komponentti]
                        (and (= :ei-riippuvuutta (:bootstrap-tila komponentti))
                          (seq (:bootstrap-luokat komponentti))))
                rekisteri/komponentit))))

(deftest luokkatunnistin-erottaa-luokan-sanasta
  (testing "luokka löytyy hiccup-avaimesta ja merkkijonosta"
    (is (luokka-kaytossa? "[:div.row {}]" "row"))
    (is (luokka-kaytossa? "{:class \"a row b\"}" "row"))
    (is (luokka-kaytossa? "[:div {:class \"col-sm-4\"}]" "col-")))
  (testing "sana dokumentaatiossa tai pidemmän nimen osana ei ole luokka"
    (is (not (luokka-kaytossa? "(defn f \"Yksi row tekstiä\" [])" "row")))
    (is (not (luokka-kaytossa? "[:div.arrow {}]" "row")))
    (is (not (luokka-kaytossa? "[:div.row-eristys {}]" "row")))))

(deftest navbar-ei-kayta-sr-only-luokkaa
  (is (not (str/includes? (slurp "src/cljs/harja/ui/bootstrap.cljs") "sr-only"))))
