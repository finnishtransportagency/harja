(ns harja.palvelin.tyokalut.lukot
  (:require [harja.kyselyt.lukot :as lukko]))

(defn aja-lukon-kanssa
  ([db tunniste toiminto-fn] (aja-lukon-kanssa db tunniste toiminto-fn nil))
  ([db tunniste toiminto-fn aikaraja]
   (if (lukko/aseta-lukko? db tunniste aikaraja)
     (do
       (toiminto-fn)
       (lukko/avaa-lukko? db tunniste)
       true)
     false)))