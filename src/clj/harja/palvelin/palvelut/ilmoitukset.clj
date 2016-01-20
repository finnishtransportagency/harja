(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-sql-time]]

            [harja.kyselyt.ilmoitukset :as q]
            [harja.palvelin.palvelut.urakat :as urakat]))

(defn hakuehto-annettu? [p]
  (if (nil? p)
    false
    (do
      (if (string? p)
        (not (empty? p))

        (if (vector? p)
          (do
            (if (empty? p)
              false
              (some true? (map hakuehto-annettu? p))))

          (if (map? p)
            (do
              (if (empty? p)
                false
                (some true? (map #(hakuehto-annettu? (val %)) p))))

            true))))))

(defn- viesti [mille mista ilman]
  (str ", "
       (if (hakuehto-annettu? mille)
         (str mista " " (pr-str mille))
         (str ilman))))

(defn hae-ilmoitukset
  [db user hallintayksikko urakka urakoitsija urakkatyyppi tilat tyypit aikavali hakuehto]
  (let [aikavali-alku (when (first aikavali)
                        (konv/sql-date (first aikavali)))
        aikavali-loppu (when (second aikavali)
                         (konv/sql-date (second aikavali)))
        urakat (urakat/kayttajan-urakat-aikavalilta db user
                                                    urakka urakoitsija urakkatyyppi hallintayksikko
                                                    (first aikavali) (second aikavali))
        tyypit (mapv name tyypit)
        viesti (str "Haetaan ilmoituksia: "
                    (viesti urakat "urakoista" "ilman urakoita")
                    (viesti aikavali-alku "alkaen" "ilman alkuaikaa")
                    (viesti aikavali-loppu "päättyen" "ilman päättymisaikaa")
                    (viesti tyypit "tyypeistä" "ilman tyyppirajoituksia")
                    (viesti hakuehto "hakusanoilla:" "ilman tekstihakua")
                    (cond
                      (:avoimet tilat) ", mutta vain avoimet."
                      (and (:suljetut tilat) (:avoimet tilat)) ", ja näistä avoimet JA suljetut."
                      (:suljetut tilat) ", ainoastaan suljetut."))
        _ (log/debug viesti)
        tulos (when-not (empty? urakat)
                (mapv
                  #(assoc % :uusinkuittaus
                            (when-not (empty? (:kuittaukset %))
                              (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
                  (konv/sarakkeet-vektoriin
                    (into []
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
                                             urakat
                                             (hakuehto-annettu? aikavali-alku) (hakuehto-annettu? aikavali-loppu)
                                             aikavali-alku aikavali-loppu
                                             (hakuehto-annettu? tyypit) tyypit
                                             (hakuehto-annettu? hakuehto) (str "%" hakuehto "%")
                                             (if (:suljetut tilat) true false) ;; Muuttaa nil arvon tai puuttuvan avaimen
                                             (if (:avoimet tilat) true false) ;; falseksi
                                             ))
                    {:kuittaus :kuittaukset})))]
    (log/debug "Löydettiin ilmoitukset: " (map :id tulos))
    (log/debug "Jokaisella on kuittauksia " (map #(count (:kuittaukset %)) tulos) "kappaletta")
    tulos))

(defn tallenna-ilmoitustoimenpide [db kayttaja ilmoitustoimenpide]
  (log/debug (format "Tallennetaan uusi ilmoitustoimenpide: %s" ilmoitustoimenpide))
  ())

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-ilmoitukset
                      (fn [user tiedot #_[{:keys [hallintayksikko urakka tilat tyypit aikavali hakuehto]} tiedot]]
                        (hae-ilmoitukset (:db this) user (:hallintayksikko tiedot)
                                         (:urakka tiedot) (:urakoitsija tiedot) (:urakkatyyppi tiedot)
                                         (:tilat tiedot) (:tyypit tiedot) (:aikavali tiedot)
                                         (:hakuehto tiedot))))
    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-ilmoitustoimenpide
                      (fn [user tiedot]
                        (tallenna-ilmoitustoimenpide (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-ilmoitukset)
    (poista-palvelut (:http-palvelin this) :tallenna-ilmoitustoimenpide)

    this))
