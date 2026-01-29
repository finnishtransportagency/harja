(ns harja.palvelin.palvelut.toteumat-tarkistukset
  (:require [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+]]))

(defn vaadi-toteuma-urakan-aikana [db toteuma-alkanut urakka-id ulkoinen-id]  
  (when toteuma-alkanut  
    (let [urakka (first (urakat-q/hae-urakka db {:id urakka-id}))  
          urakan-loppupvm (:loppupvm urakka)  
          onko-olemassa? (when ulkoinen-id   
                          (toteumat-q/onko-olemassa-ulkoisella-idlla? db ulkoinen-id urakka-id))]  
      (when (and urakan-loppupvm (not onko-olemassa?))  
        (let [sallittu-viimeinen-pvm (-> urakan-loppupvm
                                       pvm/joda-timeksi
                                       pvm/suomen-aikavyohykkeeseen
                                       (pvm/ajan-muokkaus true 1 :paiva)  
                                       pvm/paivan-lopussa  
                                       pvm/millisekunteina)  
              toteuma-alkanut-ms (.getTime toteuma-alkanut)]
          (when (> toteuma-alkanut-ms sallittu-viimeinen-pvm)  
            (throw+ {:type virheet/+viallinen-kutsu+  
                     :virheet [{:koodi virheet/+virheellinen-paivamaara+  
                                :viesti "Urakka on päättynyt ja kirjaaminen estetty."}]})))))))

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

(defn vaadi-erilliskustannus-kuuluu-urakkaan [db erilliskustannus-id vaitetty-urakka-id]
  (log/debug "Tarkistetaan, että erilliskustannus " erilliskustannus-id " kuuluu väitettyyn urakkaan " vaitetty-urakka-id)
  (assert vaitetty-urakka-id "Urakka id puuttuu!")
  (when erilliskustannus-id
    (let [urakka-id-kannassa (:urakka (first
                                        (toteumat-q/erilliskustannuksen-urakka
                                          db {:id erilliskustannus-id})))]
      (when (and (some? urakka-id-kannassa)
                 (not= urakka-id-kannassa vaitetty-urakka-id))
        (throw (SecurityException. (str "Erilliskustannus ei kuulu väitettyyn urakkaan " vaitetty-urakka-id
                                        " vaan urakkaan " urakka-id-kannassa)))))))
