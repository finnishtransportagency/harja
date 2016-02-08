(ns harja.palvelin.tyokalut.lukot
  (:require [harja.kyselyt.lukot :as lukko]))

(defn aja-toiminto [db tunniste toiminto-fn]
  (try
    (toiminto-fn)
    (catch Exception e
      (throw e))
    (finally
      (lukko/avaa-lukko? db tunniste))))

(defn aja-lukon-kanssa
  ([db tunniste toiminto-fn] (aja-lukon-kanssa db tunniste toiminto-fn nil))
  ([db tunniste toiminto-fn vanhenemisaika]
   (if (lukko/aseta-lukko? db tunniste vanhenemisaika)
     (do
       (aja-toiminto db tunniste toiminto-fn)
       true)
     false))
  ([db tunniste toiminto-fn vanhenemisaika odotusvali]
   (let [odotusvali (* odotusvali 1000)]
     (loop []
       (if (lukko/aseta-lukko? db tunniste vanhenemisaika)
         (do
           (aja-toiminto db tunniste toiminto-fn))
         (do
           (Thread/sleep odotusvali)
           (recur)))))))