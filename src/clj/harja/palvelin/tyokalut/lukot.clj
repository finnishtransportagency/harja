(ns harja.palvelin.tyokalut.lukot
  (:require [harja.kyselyt.lukot :as lukko]))

(defn aja-lukon-kanssa
  ([db tunniste toiminto-fn] (aja-lukon-kanssa db tunniste toiminto-fn nil))
  ([db tunniste toiminto-fn aikaraja]
   (if (lukko/aseta-lukko? db tunniste aikaraja)
     (do
       (try
         (toiminto-fn)
         (finally
           (lukko/avaa-lukko? db tunniste)))
       true)
     false)))

(defn aja-tietokantalukon-kanssa [db tunniste toiminto-fn]
  (lukko/aseta-tietokantalukko db tunniste)
  (try
    (let [tulos (toiminto-fn)]
      tulos)
    (finally
      (lukko/avaa-tietokantalukko db tunniste))))

(def lukkoidt {:sampo 10001})



