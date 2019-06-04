(ns harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet
  "Valtakunnallisten välitavoitteiden asettaminen tuodulle Sampo-urakalle"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.valitavoitteet :as valitavoitteet-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.valitavoitteet.valtakunnalliset-valitavoitteet :as valtakunnallinen-vt-palvelu]
            [harja.kyselyt.konversio :as konv])
  (:use [slingshot.slingshot :only [throw+]]))

(defn lisaa-urakalle-puuttuvat-valtakunnalliset-valitavoitteet [db sampo-id]
  (let [urakka (first (into []
                            (map #(konv/string->keyword % :tyyppi))
                            (urakat-q/hae-urakan-perustiedot-sampo-idlla db sampo-id)))
        valtakunnalliset-vt (into []
                                  (map #(konv/string->keyword % :urakkatyyppi :tyyppi))
                                  (valitavoitteet-q/hae-valtakunnalliset-valitavoitteet db))]
    (doseq [valtakunnallinen-vt valtakunnalliset-vt]
      (let [linkitetyt (valitavoitteet-q/hae-valitavoitteeseen-linkitetyt-valitavoitteet-urakassa
                         db
                         (:id valtakunnallinen-vt)
                         (:id urakka))]
        (when (empty? linkitetyt)
          ;; Valtakunnallista välitavoitetta ei ole liitetty urakkaan, lisätään se.
          (cond (= (:tyyppi valtakunnallinen-vt) :kertaluontoinen)
                (valtakunnallinen-vt-palvelu/kopioi-valtakunnallinen-kertaluontoinen-valitavoite-sopiviin-urakoihin
                  db
                  nil
                  valtakunnallinen-vt
                  (:id valtakunnallinen-vt)
                  [urakka])
                (= (:tyyppi valtakunnallinen-vt) :toistuva)
                (valtakunnallinen-vt-palvelu/kopioi-valtakunnallinen-toistuva-valitavoite-sopiviin-urakoihin
                  db
                  nil
                  valtakunnallinen-vt
                  (:id valtakunnallinen-vt)
                  [urakka])))))))

(defn kasittele-urakan-valitavoitteet [db sampo-id]
  (log/debug "Käsitellään sampo-id:n " sampo-id " urakan valtakunnalliset välitavoitteet")
  (lisaa-urakalle-puuttuvat-valtakunnalliset-valitavoitteet db sampo-id))
