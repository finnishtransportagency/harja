(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-sql-time]]

            [harja.kyselyt.ilmoitukset :as q]))

(defn annettu? [p]
  (if (nil? p)
    false
    (do
      (if (string? p)
        (not (empty? p))

        (if (vector? p)
          (do
            (if (empty? p)
              false
              (some true? (map annettu? p))))

          (if (map? p)
            (do
              (if (empty? p)
                false
                (some true? (map #(annettu? (val %)) p))))

            true))))))

(defn- viesti [mille mista ilman]
  (str ", "
       (if (annettu? mille)
         (str mista " " (pr-str mille))
         (str ilman))))

(defn hae-ilmoitukset
  [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
  (let [aikavali-alku (when (first aikavali)
                        (konv/sql-date (first aikavali)))
        aikavali-loppu (when (second aikavali)
                         (konv/sql-date (second aikavali)))
        tyypit (mapv name tyypit)
        viesti (str "Haetaan ilmoituksia: "
                    (viesti hallintayksikko "hallitanyksiköstä" "ilman hallintayksikköä")
                    (viesti urakka "urakasta" "ilman urakkaa")
                    (viesti aikavali-alku "alkaen" "ilman alkuaikaa")
                    (viesti aikavali-loppu "päättyen" "ilman päättymisaikaa")
                    (viesti tyypit "tyypeistä" "ilman tyyppirajoituksia")
                    (viesti hakuehto "hakusanoilla:" "ilman tekstihakua")
                    (cond
                      (:avoimet tilat) ", mutta vain avoimet."
                      (and (:suljetut tilat) (:avoimet tilat)) ", ja näistä avoimet JA suljetut."
                      (:suljetut tilat) ", ainoastaan suljetut."))
        _ (log/debug viesti)
        mankeloitava (into []
                           (comp
                             (harja.geo/muunna-pg-tulokset :sijainti)
                             (map konv/alaviiva->rakenne)
                             (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                             (map #(konv/array->vec % :selitteet))
                             (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
                             (map #(assoc-in % [:kuittaus :kuittaustyyppi] (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
                             (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                             (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi])))))
                           (q/hae-ilmoitukset db
                                              (annettu? hallintayksikko) (annettu? urakka)
                                              hallintayksikko urakka
                                              (annettu? aikavali-alku) (annettu? aikavali-loppu)
                                              aikavali-alku aikavali-loppu
                                              (annettu? tyypit) tyypit
                                              (annettu? hakuehto) (str "%" hakuehto "%")
                                              (if (:suljetut tilat) true false) ;; Muuttaa nil arvon tai puuttuvan avaimen
                                              (if (:avoimet tilat) true false) ;; falseksi
                                              ))
        mankeloitu (konv/sarakkeet-vektoriin mankeloitava {:kuittaus :kuittaukset})
        tulos (mapv
                #(assoc % :uusinkuittaus
                          (when-not (empty? (:kuittaukset %))
                            (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
                mankeloitu)]
    (log/debug "Löydettiin ilmoitukset: " (map :id mankeloitu))
    (log/debug "Jokaisella on kuittauksia " (map #(count (:kuittaukset %)) tulos) "kappaletta")
    tulos))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-ilmoitukset
                      (fn [user tiedot #_[{:keys [hallintayksikko urakka tilat tyypit aikavali hakuehto]} tiedot]]
                        (hae-ilmoitukset (:db this) user (:hallintayksikko tiedot)
                                         (:urakka tiedot) (:tilat tiedot) (:tyypit tiedot) (:aikavali tiedot)
                                         (:hakuehto tiedot))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset)

    this))
