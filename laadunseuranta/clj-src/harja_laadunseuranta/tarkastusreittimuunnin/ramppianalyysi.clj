(ns harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi
  "Tämä namespace tarjoaa funktion laadunseurannan mobiilityökalulla tehtyjen
   reittimerkintöjen ja niihin liittyvän geometrisoinnin korjaamiseen, mikäli
   geometrisointi on osunut virheellisesti rampille."
  (:require [taoensso.timbre :as log]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.utils :as utils]
            [harja.kyselyt.tarkastukset :as tark-q]
            [harja.domain.tierekisteri :as tr-domain]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn- lisaa-merkintoihin-ramppitiedot [merkinnat]
  (mapv #(assoc % :piste-rampilla? (tr-domain/tie-rampilla? %))
        merkinnat))

(defn korjaa-virheelliset-merkinnat [merkinnat]
  (let [merkinnat-ramppitiedoilla (lisaa-merkintoihin-ramppitiedot merkinnat)
        lopputulos merkinnat]
    lopputulos))