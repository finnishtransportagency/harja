(ns harja.palvelin.tyokalut.lukot
  "Lukkojen käyttäminen tietokannan kanssa. Mahdollistaa joko toiminnon ajamisen niin, että se ajetaan vain, jos
  lukko saadaan asetettua tai että jäädään odottamana lukon avautumista. Tarvitaan mm. monistetussa
  palvelinympäristössä, jossa tiettyjä toimintoja halutaan ajaa vain yhdellä nodella kerrallaan. Oletuksena kaikki lukot
  vanhenevat tunnin kuluessa."
  (:require [harja.kyselyt.lukot :as lukko]
            [taoensso.timbre :as log]))

(defn aja-toiminto [db tunniste toiminto-fn]
  (try
    (toiminto-fn)
    (catch Exception e
      (throw e))
    (finally
      (log/info "aja-toiminto valmis tunnisteella: " tunniste)
      ;; Jotta vältetään turha uudestaan ajaminen app1 ja app2, ei avata lukkoa toiminnon valmistuttua,
      ;; vaan nojataan lukon vanhenemisaikaan, joka tarkistetaan aseta-lukko SQL-funktiossa.
      ;(lukko/avaa-lukko? db tunniste)
      )))

;; Tämän pitäisi olla optimaalinen default-aika, joka riittä estämään useimmat duplikaattiajot usean noden ympäristössä
;; duplikaatteja on syntynyt erityisesti toistuvaa ajasta-minuutin-valein funktiota käytettäessä
;; 2 minuuttia, eli sekunnit * minuutit, ks. SQL-funktio aseta_lukko
(def default-lukon-vanhenemisaika (* 60 2))

(defn yrita-ajaa-lukon-kanssa
  "Yritä ajaa annettu funktio lukon kanssa. Jos lukko on lukittuna, ei toimintoa ajeta.
  Palauttaa true jos toiminto ajettiin, false muuten.
  Huom! Vanhenemisaika täytyy aina antaa, jotta lukko ei jää virhetilanteessa ikuisesti kiinni."
  ([db tunniste toiminto-fn] (yrita-ajaa-lukon-kanssa db tunniste toiminto-fn default-lukon-vanhenemisaika))
  ([db tunniste toiminto-fn vanhenemisaika]
   (if (lukko/aseta-lukko? db tunniste vanhenemisaika)
     (do
       (log/info (format "Lukkoa: %s ei ole asetettu. Voidaan ajaa toiminto." tunniste))
       (aja-toiminto db tunniste toiminto-fn)
       true)
     (do
       (log/info (format "Lukko: %s on asetettu. Toimintoa ei voida ajaa." tunniste))
       false))))

(defn aja-lukon-kanssa
  "Ajaa toiminnon lukon kanssa. Odottaa kunnes lukko on vapaana.
  Huom! Vanhenemisaika täytyy aina antaa, jotta lukko ei jää virhetilanteessa ikuisesti kiinni."
  ([db tunniste toiminto-fn]
   (aja-lukon-kanssa db tunniste toiminto-fn default-lukon-vanhenemisaika))
  ([db tunniste toiminto-fn vanhenemisaika]
   (aja-lukon-kanssa db tunniste toiminto-fn vanhenemisaika 1))
  ([db tunniste toiminto-fn vanhenemisaika odotusvali]
   (let [odotusvali (* odotusvali 1000)]
     (loop []
       (if (lukko/aseta-lukko? db tunniste vanhenemisaika)
         (aja-toiminto db tunniste toiminto-fn)
         (do
           (Thread/sleep odotusvali)
           (recur)))))))