(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi
  "Tämä namespace tarjoaa funktion laadunseurannan mobiilityökalulla tehtyjen
   reittimerkintöjen ja niihin liittyvän geometrisoinnin korjaamiseen, mikäli
   geometrisointi on osunut virheellisesti rampille."
  (:require [taoensso.timbre :as log]
            [harja.domain.tierekisteri :as tr-domain]))

(defn- lisaa-merkintoihin-ramppitiedot [merkinnat]
  (mapv #(assoc % :piste-rampilla? (tr-domain/tie-rampilla? %))
        merkinnat))

(defn korjaa-virheelliset-rampit [merkinnat]
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        lopputulos merkinnat]
    lopputulos))