(ns harja.tyokalut.testidatan-kaytto
  "Apureita testidatan käyttämiseen, eli tallenna, poista, muokkkaa"
  (:require [clojure.string :as str]
            [harja.testi :refer :all]))

  ;; Helpottaa testien roskien keruuta. Toisinaan kun omalla koneella ajaa kaikki testit useampaan kertaan,
;; jäävät siivoamattomat sanktiot testikantaan vääristämään tuloksia
(defn poista-sanktio-perustelulla
  "Poistaa laatupoikkeaman perustelukentän sisällön mukaan tunnistaen, ja siihen liittyvät sanktiot."
  [perustelu]
  (let [laatupoikkeama-idt (map first (q (str "SELECT id FROM laatupoikkeama where perustelu = '" perustelu "';")))
        sanktio-idt (when
                      (seq laatupoikkeama-idt)
                      (map first (q (str "SELECT id FROM sanktio where laatupoikkeama IN (" (str/join "," laatupoikkeama-idt) ");"))))]

(when (seq sanktio-idt)
  (u "DELETE FROM sanktio WHERE id IN (" (str/join "," sanktio-idt) ");"))
(when (seq laatupoikkeama-idt)
  (u "DELETE FROM laatupoikkeama WHERE id IN(" (str/join "," laatupoikkeama-idt) ");"))))

(defn poista-bonus-idlla [bonus-id]
  (u (format "DELETE FROM erilliskustannus WHERE id = %s;" bonus-id)))

(defn poista-tavoitehinnan-muutos-idlla [tavoitehinnan-muutos-id]
  (u (format "DELETE FROM tavoitehinnan_oikaisu WHERE id = %s;" tavoitehinnan-muutos-id)))

(defn poista-paatos-idlla [paatos-id]
  (u (format "DELETE FROM urakka_paatos WHERE id = %s;" paatos-id)))
