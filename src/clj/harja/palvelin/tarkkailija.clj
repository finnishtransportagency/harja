(ns harja.palvelin.tarkkailija
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.palvelut.tarkastelija.tapahtuma :as tapahtuma]
            [harja.palvelin.komponentit.jarjestelma-rajapinta :as rajapinta]
            [harja.palvelin.komponentit.uudelleen-kaynnistaja :as uudelleen-kaynnistaja]))

(defonce harja-tarkkailija nil)

(defn kaynnista! [{:keys [tietokanta tarkkailija komponenttien-tila]}]
  (alter-var-root #'harja-tarkkailija
                  (constantly
                    (component/start
                      (component/system-map
                        :db-event (event-tietokanta/luo-tietokanta tietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat tarkkailija)
                                                {:db :db-event})
                        :tapahtuma (component/using
                                     (tapahtuma/->Tapahtuma)
                                     [:klusterin-tapahtumat :rajapinta])
                        :rajapinta (rajapinta/->Rajapintakasittelija)
                        :uudelleen-kaynnistaja (uudelleen-kaynnistaja/->UudelleenKaynnistaja komponenttien-tila (atom nil)))))))

(defn sammuta! []
  (alter-var-root #'harja-tarkkailija (fn [s]
                                        (component/stop s)
                                        nil)))

