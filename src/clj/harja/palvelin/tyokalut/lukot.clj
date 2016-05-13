(ns harja.palvelin.tyokalut.lukot
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
  Palauttaa true jos toiminto ajettiin, false muuten"
  [db tunniste toiminto-fn]
  (if (lukko/aseta-lukko? db tunniste nil)
    (do
      (log/debug (format "Lukkoa: %s ei ole asetettu. Voidaan ajaa toiminto." tunniste))
      (aja-toiminto db tunniste toiminto-fn)
      true)
    (do
      (log/debug (format "Lukkoa: %s on asetettu. Toimintoa ei voida ajaa." tunniste))
      false)))

(defn aja-lukon-kanssa
  "Ajaa toiminnon lukon kanssa. Odottaa kunnes lukko on vapaana."
  ([db tunniste toiminto-fn]
   (aja-lukon-kanssa db tunniste toiminto-fn nil))
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
