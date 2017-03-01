(ns harja.palvelin.palvelut.toteumat-tarkistukset
  (:require [harja.kyselyt.toteumat :as toteumat-q]
            [taoensso.timbre :as log]))


(defn vaadi-toteuma-ei-jarjestelman-luoma [db toteuma-id]
  (log/debug "Tarkistetaan, ettei toteuma " toteuma-id " ole järjestelmästä tullut")
  (when toteuma-id
    (let [jarjestelman-lisaama? (:jarjestelmanlisaama (first
                                                        (toteumat-q/toteuma-jarjestelman-lisaama
                                                          db {:toteuma toteuma-id})))]
      (when jarjestelman-lisaama?
        (throw (SecurityException. "Järjestelmän luomaa toteumaa ei voi muokata!"))))))

(defn vaadi-toteuma-kuuluu-urakkaan [db toteuma-id vaitetty-urakka-id]
  (log/debug "Tarkikistetaan, että toteuma " toteuma-id " kuuluu väitettyyn urakkaan " vaitetty-urakka-id)
  (assert vaitetty-urakka-id "Urakka id puuttuu!")
  (when toteuma-id
    (let [toteuman-todellinen-urakka-id (:urakka (first
                                                   (toteumat-q/toteuman-urakka
                                                     db {:toteuma toteuma-id})))]
      (when (and (some? toteuman-todellinen-urakka-id)
                 (not= toteuman-todellinen-urakka-id vaitetty-urakka-id))
        (throw (SecurityException. (str "Toteuma ei kuulu väitettyyn urakkaan " vaitetty-urakka-id
                                        " vaan urakkaan " toteuman-todellinen-urakka-id)))))))