(ns harja.palvelin.palvelut.paikkaus
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paallystys.pot :as pot]

            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]))

(defn hae-urakan-paikkaustoteumat [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan paikkaustoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string->avain % [:paatos]))
                        (map #(konv/string->avain % [:tila])))
                      (q/hae-urakan-paikkaustoteumat db urakka-id sopimus-id))]
    (log/debug "Paikkaustoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn
  hae-urakan-paikkausilmoitus-paikkauskohteella [db user {:keys [urakka-id sopimus-id paikkauskohde-id]}]
  (log/debug "Haetaan urakan paikkausilmoitus, jonka paikkauskohde-id " paikkauskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [paikkausilmoitus (first (into []
                                        (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                              (map #(konv/string->avain % [:tila]))
                                              (map #(konv/string->avain % [:paatos])))
                                        (q/hae-urakan-paikkausilmoitus-paikkauskohteella db urakka-id sopimus-id paikkauskohde-id)))]
    (log/debug "Paikkausilmoitus saatu: " (pr-str paikkausilmoitus))
    (when paikkausilmoitus
      (log/debug "Haetaan kommentit...")
      (let [kommentit (into []
                            (comp (map konv/alaviiva->rakenne)
                                  (map (fn [{:keys [liite] :as kommentti}]
                                         (if (:id
                                               liite)
                                           kommentti
                                           (dissoc kommentti :liite)))))
                            (q/hae-paikkausilmoituksen-kommentit db (:id paikkausilmoitus)))]
        (log/debug "Kommentit saatu: " kommentit)
        (assoc paikkausilmoitus :kommentit kommentit))
        (assoc paikkausilmoitus :kommentit []))))

(defrecord Paikkaus []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paikkaustoteumat
                        (fn [user tiedot]
                          (hae-urakan-paikkaustoteumat db user tiedot)))
      (julkaise-palvelu http :urakan-paikkausilmoitus-paikkauskohteella
                        (fn [user tiedot]
                          (hae-urakan-paikkausilmoitus-paikkauskohteella db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paikkaustoteumat
      :urakan-paikkausilmoitus-paikkauskohteella)
    this))