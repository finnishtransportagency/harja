(ns harja.ui.komponentit.bootstrap-luokat-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.komponentit.rekisteri :as rekisteri]))

(defn- komponentin-lahdekoodi [{:keys [funktio]}]
  (let [ns-nimi (first (str/split funktio #"/"))
        polku (str "src/cljs/" (-> ns-nimi (str/replace "." "/") (str/replace "-" "_")))]
    (or (some #(when (.exists (java.io.File. (str polku %))) (slurp (str polku %))) [".cljs" ".cljc"])
        "")))

(deftest bootstrap-luokat-loytyvat-komponentin-lahdekoodista
  (testing "modal ilmoittaa käyttämänsä Bootstrap-luokat"
    (is (seq (:bootstrap-luokat (rekisteri/hae :modal)))))
  (doseq [komponentti (filter :bootstrap-luokat rekisteri/komponentit)
          :let [lahde (komponentin-lahdekoodi komponentti)]
          luokka (:bootstrap-luokat komponentti)]
    (is (str/includes? lahde luokka)
      (str (:id komponentti) ": luokkaa " luokka " ei löydy lähdekoodista"))))

(deftest ei-riippuvuutta-ei-listaa-bootstrap-luokkia
  (is (empty? (filter (fn [komponentti]
                        (and (= :ei-riippuvuutta (:bootstrap-tila komponentti))
                          (seq (:bootstrap-luokat komponentti))))
                rekisteri/komponentit))))

(deftest navbar-ei-kayta-sr-only-luokkaa
  (is (not (str/includes? (slurp "src/cljs/harja/ui/bootstrap.cljs") "sr-only"))))
