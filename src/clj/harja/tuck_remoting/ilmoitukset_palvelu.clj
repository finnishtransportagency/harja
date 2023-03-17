(ns harja.tuck-remoting.ilmoitukset-palvelu
  (:require [harja.palvelin.komponentit.tuck-remoting :as tuck-remoting]
            [tuck.remoting :as tr]
            [harja.tuck-remoting.ilmoitukset-eventit :as eventit]
            [com.stuartsierra.component :as component]))

(defn laheta-ilmoitus! [e! opts]
  (e! (eventit/->Ilmoitus opts)))

(defrecord IlmoituksetWS []
  component/Lifecycle
  (start [{tuck-remoting :tuck-remoting :as this}]
    (assoc this ::stop
                (tuck-remoting/rekisteroi-yhdistaessa-hook!
                  tuck-remoting
                  (fn [{::tr/keys [e!]}]
                    (laheta-ilmoitus! e! {:tyyppi :terve
                                          :sisalto "Hei!"})))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop) ) )

(defn tee-ilmoitusws []
  (->IlmoituksetWS))

