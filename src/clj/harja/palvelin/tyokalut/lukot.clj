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
      (lukko/avaa-lukko? db tunniste))))

(defn yrita-ajaa-lukon-kanssa
  "Yritä ajaa annettu funktio lukon kanssa. Jos lukko on lukittuna, ei toimintoa ajeta.
  Palauttaa true jos toiminto ajettiin, false muuten.
  Oletuksena lukonvanhenemisaika on tunti.
  Huom! Vanhenemisaika täytyy aina antaa, jotta lukko ei jää virhetilanteessa ikuisesti kiinni."
  ([db tunniste toiminto-fn] (yrita-ajaa-lukon-kanssa db tunniste toiminto-fn 60))
  ([db tunniste toiminto-fn vanhenemisaika]
   (if (lukko/aseta-lukko? db tunniste vanhenemisaika)
     (do
       (log/debug (format "Lukkoa: %s ei ole asetettu. Voidaan ajaa toiminto." tunniste))
       (aja-toiminto db tunniste toiminto-fn)
       true)
     (do
       (log/debug (format "Lukko: %s on asetettu. Toimintoa ei voida ajaa." tunniste))
       false))))

(defn aja-lukon-kanssa
  "Ajaa toiminnon lukon kanssa. Odottaa kunnes lukko on vapaana.
  Oletuksena lukonvanhenemisaika on tunti.
  Huom! Vanhenemisaika täytyy aina antaa, jotta lukko ei jää virhetilanteessa ikuisesti kiinni."
  ([db tunniste toiminto-fn]
   (aja-lukon-kanssa db tunniste toiminto-fn 60))
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
