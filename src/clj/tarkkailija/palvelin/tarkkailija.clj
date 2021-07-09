(ns tarkkailija.palvelin.tarkkailija
  (:require [com.stuartsierra.component :as component]
            [tarkkailija.palvelin.komponentit.event-tietokanta :as event-tietokanta]
            [tarkkailija.palvelin.komponentit.tapahtumat :as tapahtumat]
            [tarkkailija.palvelin.palvelut.tapahtuma :as tapahtuma]
            [tarkkailija.palvelin.komponentit.jarjestelma-rajapinta :as rajapinta]
            [tarkkailija.palvelin.komponentit.uudelleen-kaynnistaja :as uudelleen-kaynnistaja]))

(defonce harja-tarkkailija nil)

(defn kaynnista! [{:keys [tietokanta tarkkailija komponenttien-tila kehitysmoodi]}]
  (alter-var-root #'harja-tarkkailija
                  (constantly
                    (component/start
                      (component/system-map
                        :db-event (event-tietokanta/luo-tietokanta tietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat {:tarkkailija tarkkailija
                                                                            :kehitysmoodi kehitysmoodi})
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

